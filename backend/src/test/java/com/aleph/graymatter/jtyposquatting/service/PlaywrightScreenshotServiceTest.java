package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.security.OutboundPolicy;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests of the Playwright screenshot service: filtering of browser requests and real capture
 * (skipped when Chromium is not installed: ./gradlew :backend:installPlaywrightBrowsers).
 */
class PlaywrightScreenshotServiceTest {

    private PlaywrightScreenshotService service;
    private WireMockServer pageServer;
    private WireMockServer forbiddenServer;

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
        if (pageServer != null) pageServer.stop();
        if (forbiddenServer != null) forbiddenServer.stop();
    }

    private static boolean chromiumInstalled() {
        String customPath = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        Path browsers = customPath != null ? Path.of(customPath) : Path.of(System.getProperty("user.home"), ".cache", "ms-playwright");
        try (var entries = Files.list(browsers)) {
            return entries.anyMatch(p -> p.getFileName().toString().startsWith("chromium"));
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Browser requests to forbidden destinations are blocked")
    void testRequestFiltering() {
        service = new PlaywrightScreenshotService(new OutboundPolicy(false, Set.of(80, 443)), true, false, 5000, 1);

        assertFalse(service.isRequestAllowed("http://127.0.0.1/"));
        assertFalse(service.isRequestAllowed("http://169.254.169.254/latest/meta-data/"));
        assertFalse(service.isRequestAllowed("http://localhost/"));
        assertFalse(service.isRequestAllowed("http://8.8.8.8:8080/"));
        assertFalse(service.isRequestAllowed("file:///etc/passwd"));
        assertFalse(service.isRequestAllowed("not a url"));
        assertTrue(service.isRequestAllowed("data:image/png;base64,iVBORw0KGgo="));
        assertTrue(service.isRequestAllowed("https://8.8.8.8/"));
    }

    @Test
    @DisplayName("A forbidden URL is never opened in the browser")
    void testBlockedUrlReturnsNull() throws Exception {
        service = new PlaywrightScreenshotService(new OutboundPolicy(false, Set.of(80, 443)), true, false, 5000, 1);

        assertNull(service.captureScreenshot(URI.create("http://127.0.0.1/").toURL()));
    }

    @Test
    @DisplayName("Screenshots disabled by configuration")
    void testDisabled() throws Exception {
        service = new PlaywrightScreenshotService(OutboundPolicy.permissive(), false, false, 5000, 1);

        assertNull(service.captureScreenshot(URI.create("https://example.com/").toURL()));
    }

    @Test
    @DisplayName("Real PNG capture, forbidden sub-resources blocked")
    void testRealCaptureBlocksForbiddenSubresources() throws Exception {
        assumeTrue(chromiumInstalled(), "Chromium not installed");

        forbiddenServer = new WireMockServer(options().dynamicPort());
        forbiddenServer.start();
        forbiddenServer.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)));

        pageServer = new WireMockServer(options().dynamicPort());
        pageServer.start();
        pageServer.stubFor(get(urlEqualTo("/page")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withBody("<html><head><title>Capture</title></head><body><h1>Hello</h1>"
                        + "<img src=\"http://127.0.0.1:" + forbiddenServer.port() + "/tracker.png\"></body></html>")));

        // Local addresses allowed for the test, but only the port of the page server is permitted
        OutboundPolicy policy = new OutboundPolicy(true, Set.of(pageServer.port()));
        service = new PlaywrightScreenshotService(policy, true, false, 15000, 1);

        byte[] png = service.captureScreenshot(URI.create("http://127.0.0.1:" + pageServer.port() + "/page").toURL());

        assertNotNull(png, "The capture must succeed");
        assertTrue(png.length > 1000);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
        assertTrue(forbiddenServer.getAllServeEvents().isEmpty(), "The forbidden sub-resource must not be loaded");
    }
}
