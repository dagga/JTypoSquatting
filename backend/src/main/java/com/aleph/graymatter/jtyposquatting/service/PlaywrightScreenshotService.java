package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.security.OutboundPolicy;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Captures screenshots with headless Chromium (Playwright).
 * <p>
 * Analysed pages are potentially hostile: each capture runs in a fresh browser context (no shared
 * cookies or storage), downloads and service workers are disabled, and every request issued by the
 * page — including sub-resources and redirects — is checked against the {@link OutboundPolicy}.
 * In production, browsers must additionally run in a network-isolated container (see plan P1.3).
 * <p>
 * Playwright objects are not thread-safe: each browser slot is used by a single thread at a time.
 */
@Service
public class PlaywrightScreenshotService implements ScreenshotService {
    private static final Logger logger = LoggerFactory.getLogger(PlaywrightScreenshotService.class);

    private final OutboundPolicy outboundPolicy;
    private final boolean enabled;
    private final boolean chromiumSandbox;
    private final int timeoutMillis;
    private final int maxConcurrent;

    private final Semaphore permits;
    private final BlockingQueue<BrowserSlot> idleSlots;
    private final List<BrowserSlot> allSlots = new ArrayList<>();
    private volatile boolean unavailable;

    public PlaywrightScreenshotService(OutboundPolicy outboundPolicy,
                                       @Value("${jtypo.screenshot.enabled:true}") boolean enabled,
                                       @Value("${jtypo.screenshot.chromium-sandbox:true}") boolean chromiumSandbox,
                                       @Value("${jtypo.screenshot.timeout-ms:15000}") int timeoutMillis,
                                       @Value("${jtypo.screenshot.max-concurrent:2}") int maxConcurrent) {
        this.outboundPolicy = outboundPolicy;
        this.enabled = enabled;
        this.chromiumSandbox = chromiumSandbox;
        this.timeoutMillis = timeoutMillis;
        this.maxConcurrent = maxConcurrent;
        this.permits = new Semaphore(maxConcurrent, true);
        this.idleSlots = new ArrayBlockingQueue<>(maxConcurrent);
    }

    @Override
    public byte[] captureScreenshot(URL url) {
        if (!enabled || unavailable || url == null || !isRequestAllowed(url.toString())) {
            return null;
        }
        try {
            if (!permits.tryAcquire(timeoutMillis * 2L, TimeUnit.MILLISECONDS)) {
                logger.warn("Screenshot skipped for {}: all browsers busy", url);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        BrowserSlot slot = null;
        try {
            slot = borrowSlot();
            return capture(slot.browser(), url.toString());
        } catch (PlaywrightException e) {
            logger.debug("Screenshot failed for {}: {}", url, e.getMessage());
            if (slot != null && !slot.browser().isConnected()) {
                discard(slot);
                slot = null;
            }
            return null;
        } finally {
            if (slot != null) {
                idleSlots.offer(slot);
            }
            permits.release();
        }
    }

    private byte[] capture(Browser browser, String url) {
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1280, 800)
                .setAcceptDownloads(false)
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
            context.setDefaultTimeout(timeoutMillis);
            context.setDefaultNavigationTimeout(timeoutMillis);
            context.route("**/*", route -> {
                if (isRequestAllowed(route.request().url())) {
                    route.resume();
                } else {
                    logger.debug("Blocked browser request to {}", route.request().url());
                    route.abort("blockedbyclient");
                }
            });
            Page page = context.newPage();
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
            return page.screenshot(new Page.ScreenshotOptions().setType(ScreenshotType.PNG));
        }
    }

    /** Only http(s) requests to allowed destinations leave the browser; data: and blob: URLs stay local. */
    boolean isRequestAllowed(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (scheme.equals("data") || scheme.equals("blob")) {
                return true;
            }
            outboundPolicy.checkUrl(uri);
            outboundPolicy.resolve(uri.getHost());
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    private BrowserSlot borrowSlot() {
        BrowserSlot slot = idleSlots.poll();
        if (slot != null) {
            return slot;
        }
        synchronized (allSlots) {
            if (allSlots.size() >= maxConcurrent) {
                // A permit guarantees a slot is idle or can be created; this only happens after a discard race
                throw new PlaywrightException("No browser slot available");
            }
            slot = launchSlot();
            allSlots.add(slot);
            return slot;
        }
    }

    private BrowserSlot launchSlot() {
        try {
            // Browsers are installed explicitly (./gradlew :backend:installPlaywrightBrowsers or the container image)
            Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setChromiumSandbox(chromiumSandbox));
            logger.info("Chromium {} started for screenshots (sandbox: {})", browser.version(), chromiumSandbox);
            return new BrowserSlot(playwright, browser);
        } catch (PlaywrightException e) {
            unavailable = true;
            logger.error("Screenshots disabled: Chromium could not be started ({}). "
                    + "Install it with ./gradlew :backend:installPlaywrightBrowsers", e.getMessage());
            throw e;
        }
    }

    private void discard(BrowserSlot slot) {
        synchronized (allSlots) {
            allSlots.remove(slot);
        }
        slot.close();
    }

    @PreDestroy
    public void shutdown() {
        synchronized (allSlots) {
            allSlots.forEach(BrowserSlot::close);
            allSlots.clear();
        }
        idleSlots.clear();
    }

    private record BrowserSlot(Playwright playwright, Browser browser) {
        void close() {
            try {
                playwright.close();
            } catch (PlaywrightException e) {
                logger.debug("Error closing Playwright: {}", e.getMessage());
            }
        }
    }
}
