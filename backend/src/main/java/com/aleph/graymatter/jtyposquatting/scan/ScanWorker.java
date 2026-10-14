package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.JTypoSquatting;
import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.persistence.DomainKeys;
import com.aleph.graymatter.jtyposquatting.service.DomainCheckService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes queued scans: generates the typo variants, checks them and stores the results.
 * <p>
 * Each worker claims scans from the database, so several workers can run side by side. A running
 * scan sends a heartbeat; when a worker dies, its scans are taken over by another worker once the
 * heartbeat is stale, and only the domains not yet checked are processed.
 */
@Component
@ConditionalOnProperty(name = "jtypo.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ScanWorker {
    private static final Logger logger = LoggerFactory.getLogger(ScanWorker.class);

    private final ScanRepository scans;
    private final ScanResultRepository results;
    private final DomainCheckService domainCheckService;
    private final int maxDomainsPerScan;
    private final Duration heartbeatInterval;
    private final Duration staleAfter;
    private final Clock clock = Clock.systemUTC();
    private final String workerId;

    private final Semaphore scanSlots;
    private final ExecutorService scanExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("scan-heartbeat").daemon().factory());
    private volatile boolean stopping;

    public ScanWorker(ScanRepository scans, ScanResultRepository results, DomainCheckService domainCheckService,
                      @Value("${jtypo.worker.max-concurrent-scans:4}") int maxConcurrentScans,
                      @Value("${jtypo.quota.max-domains-per-scan:3000}") int maxDomainsPerScan,
                      @Value("${jtypo.worker.heartbeat-interval-ms:5000}") long heartbeatIntervalMillis,
                      @Value("${jtypo.worker.stale-after-ms:120000}") long staleAfterMillis) {
        this.scans = scans;
        this.results = results;
        this.domainCheckService = domainCheckService;
        this.maxDomainsPerScan = maxDomainsPerScan;
        this.heartbeatInterval = Duration.ofMillis(heartbeatIntervalMillis);
        this.staleAfter = Duration.ofMillis(staleAfterMillis);
        this.scanSlots = new Semaphore(maxConcurrentScans);
        this.workerId = hostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String getWorkerId() {
        return workerId;
    }

    /** Claims as many queued scans as there are free slots. */
    @Scheduled(fixedDelayString = "${jtypo.worker.poll-interval-ms:1000}")
    public void poll() {
        if (stopping || scanSlots.availablePermits() == 0) {
            return;
        }
        Instant now = clock.instant();
        for (String scanId : scans.findClaimable(now.minus(staleAfter), scanSlots.availablePermits())) {
            if (!scanSlots.tryAcquire()) {
                return;
            }
            if (scans.claim(scanId, workerId, now, now.minus(staleAfter))) {
                scanExecutor.submit(() -> {
                    try {
                        run(scanId);
                    } finally {
                        scanSlots.release();
                    }
                });
            } else {
                // Claimed by another worker in the meantime
                scanSlots.release();
            }
        }
    }

    void run(String scanId) {
        Scan scan = scans.findById(scanId).orElse(null);
        if (scan == null) {
            return;
        }
        AtomicInteger checked = new AtomicInteger(results.countByScan(scanId));
        AtomicBoolean stopped = new AtomicBoolean();
        ExecutorService checks = Executors.newVirtualThreadPerTaskExecutor();
        var heartbeat = heartbeats.scheduleAtFixedRate(() -> {
            if (!scans.heartbeat(scanId, workerId, checked.get(), clock.instant())) {
                // Cancelled by the user, or taken over by another worker
                stopped.set(true);
                checks.shutdownNow();
            }
        }, heartbeatInterval.toMillis(), heartbeatInterval.toMillis(), TimeUnit.MILLISECONDS);
        try {
            List<String> domains = generate(scan);
            scans.setTotal(scanId, workerId, domains.size());
            Set<String> alreadyChecked = results.findCheckedDomains(scanId);
            logger.info("Scan {} of {}: {} domains ({} already checked)", scanId, scan.baseDomain(), domains.size(), alreadyChecked.size());

            List<Future<?>> futures = new ArrayList<>();
            for (String domain : domains) {
                if (alreadyChecked.contains(domain)) {
                    continue;
                }
                futures.add(checks.submit(() -> {
                    if (stopped.get() || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    DomainResultDTO result = domainCheckService.analyze(domain);
                    if (!stopped.get() && results.insert(toScanResult(scanId, domain, result))) {
                        checked.incrementAndGet();
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    if (stopped.get()) {
                        break;
                    }
                    logger.debug("Check failed in scan {}: {}", scanId, e.getMessage());
                }
            }
            if (!stopped.get()) {
                scans.finish(scanId, workerId, ScanStatus.COMPLETED, checked.get(), null, clock.instant());
                logger.info("Scan {} completed: {} domains checked", scanId, checked.get());
            }
        } catch (Exception e) {
            logger.error("Scan {} failed", scanId, e);
            scans.finish(scanId, workerId, ScanStatus.FAILED, checked.get(), truncate(e.getMessage()), clock.instant());
        } finally {
            heartbeat.cancel(false);
            checks.shutdownNow();
        }
    }

    /** Typo variants in ASCII/punycode form, without the scanned domain, capped to the configured maximum. */
    List<String> generate(Scan scan) throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        for (String domain : new JTypoSquatting(scan.baseDomainUnicode()).getListOfDomains()) {
            try {
                keys.add(DomainKeys.toKey(domain));
            } catch (IllegalArgumentException e) {
                logger.debug("Skipping {}: not convertible to ASCII", domain);
            }
        }
        keys.remove(scan.baseDomain());
        List<String> domains = new ArrayList<>(keys);
        if (domains.size() > maxDomainsPerScan) {
            logger.info("Scan {} limited to {} of {} generated domains", scan.id(), maxDomainsPerScan, domains.size());
            domains = domains.subList(0, maxDomainsPerScan);
        }
        return domains;
    }

    private ScanResult toScanResult(String scanId, String domain, DomainResultDTO result) {
        DomainStatus status = result.getStatus() != null ? result.getStatus() : DomainStatus.UNKNOWN;
        return new ScanResult(0, scanId, domain, DomainKeys.toUnicode(domain), status, result.getHttpCode(),
                result.getTlsStatus(), result.getTitle(), result.getLanguage(), result.getDescription(),
                result.getHomepageText(), result.getHttpHeaders(), null, clock.instant());
    }

    @PreDestroy
    public void stop() {
        // Running scans keep their RUNNING status: another worker takes them over once the heartbeat is stale
        stopping = true;
        heartbeats.shutdownNow();
        scanExecutor.shutdownNow();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 499) + "…" : message;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "worker";
        }
    }
}
