package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;
import com.aleph.graymatter.jtyposquatting.service.DomainCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Scan processing by the worker: complete run, cancellation, takeover after a worker failure.
 * Network checks are simulated.
 */
@SpringBootTest(properties = {
        "jtypo.worker.enabled=true",
        "jtypo.worker.poll-interval-ms=100",
        "jtypo.worker.heartbeat-interval-ms=100",
        "jtypo.worker.stale-after-ms=1000",
        "jtypo.quota.max-domains-per-scan=200",
        "jtypo.quota.max-concurrent-scans=10"})
@ActiveProfiles("test")
class ScanWorkerTest {

    @MockitoBean
    private DomainCheckService domainCheckService;

    @Autowired
    private ScanService scanService;

    @Autowired
    private ScanRepository scans;

    @Autowired
    private ScanResultRepository results;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        when(domainCheckService.analyze(anyString())).thenAnswer(invocation -> {
            String domain = invocation.getArgument(0);
            DomainResultDTO result = new DomainResultDTO(domain, DomainStatus.SAFE, "Title", "ENGLISH", "", 301, null, "", null);
            result.setTlsStatus(TlsStatus.VALID);
            return result;
        });
    }

    private static void await(BooleanSupplier condition, String message) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                fail("Timeout: " + message);
            }
            Thread.sleep(50);
        }
    }

    private ScanStatus statusOf(String scanId) {
        return scans.findById(scanId).orElseThrow().status();
    }

    @Test
    @DisplayName("A scan is processed to the end, each domain once")
    void testScanCompletes() throws Exception {
        Scan scan = scanService.createScan(new ScanUser("worker-user", "w", null), "complete-test.com", false);

        await(() -> statusOf(scan.id()) == ScanStatus.COMPLETED, "scan completed");

        Scan done = scans.findById(scan.id()).orElseThrow();
        assertEquals(200, done.totalDomains(), "capped by max-domains-per-scan");
        assertEquals(200, done.checkedDomains());
        List<ScanResult> stored = results.findAfter(scan.id(), 0, 1000);
        assertEquals(200, stored.size());
        Set<String> domains = new HashSet<>();
        stored.forEach(r -> assertTrue(domains.add(r.domain()), "doublon " + r.domain()));
        assertFalse(domains.contains("complete-test.com"));
        assertNotNull(done.startedAt());
        assertNotNull(done.finishedAt());
    }

    @Test
    @DisplayName("A cancelled scan stops without being marked completed")
    void testCancellation() throws Exception {
        when(domainCheckService.analyze(anyString())).thenAnswer(invocation -> {
            Thread.sleep(200);
            return new DomainResultDTO(invocation.getArgument(0), DomainStatus.UNREACHABLE, "", "", "", 0, null, "", null);
        });
        Scan scan = scanService.createScan(new ScanUser("cancel-user", "c", null), "cancel-test.org", false);
        await(() -> statusOf(scan.id()) == ScanStatus.RUNNING, "scan running");

        assertTrue(scans.cancel(scan.id(), Instant.now()));
        Thread.sleep(1000);

        assertEquals(ScanStatus.CANCELLED, statusOf(scan.id()));
        int checkedAfterCancel = results.countByScan(scan.id());
        Thread.sleep(500);
        assertEquals(checkedAfterCancel, results.countByScan(scan.id()), "no more results after the cancellation");
        assertTrue(checkedAfterCancel < 200);
    }

    @Test
    @DisplayName("A scan abandoned by a failed worker is taken over without checking the done domains again")
    void testTakeoverOfStaleScan() throws Exception {
        Scan scan = scanService.createScan(new ScanUser("takeover-user", "t", null), "takeover-test.net", false);
        // Simulates a dead worker: RUNNING scan, old heartbeat, one result already stored
        Instant longAgo = Instant.now().minus(Duration.ofMinutes(10));
        jdbc.update("UPDATE scans SET status = 'RUNNING', worker_id = 'dead-worker', heartbeat_at = ?, started_at = ? WHERE id = ?",
                Timestamp.from(longAgo), Timestamp.from(longAgo), scan.id());
        results.insert(new ScanResult(0, scan.id(), "takever-test.net", "takever-test.net", DomainStatus.SAFE, 200,
                TlsStatus.VALID, null, null, null, null, null, null, longAgo));

        await(() -> statusOf(scan.id()) == ScanStatus.COMPLETED, "scan taken over and completed");

        verify(domainCheckService, never()).analyze("takever-test.net");
        assertEquals(200, results.countByScan(scan.id()));
    }

    @Test
    @DisplayName("Shared cache: a second scan of the same domain checks nothing again, unless forceRefresh")
    void testSharedCache() throws Exception {
        Scan first = scanService.createScan(new ScanUser("cache-user-1", "a", null), "cache-test.com", false);
        await(() -> statusOf(first.id()) == ScanStatus.COMPLETED, "first scan completed");
        verify(domainCheckService, times(200)).analyze(anyString());
        clearInvocations(domainCheckService);

        // Another user, same domain: everything comes from the cache
        Scan second = scanService.createScan(new ScanUser("cache-user-2", "b", null), "cache-test.com", false);
        await(() -> statusOf(second.id()) == ScanStatus.COMPLETED, "second scan completed");
        verify(domainCheckService, never()).analyze(anyString());
        assertEquals(200, results.countByScan(second.id()));
        assertEquals("Title", results.findAfter(second.id(), 0, 1).get(0).title());

        // forceRefresh: every domain is checked again
        Scan third = scanService.createScan(new ScanUser("cache-user-3", "c", null), "cache-test.com", true);
        await(() -> statusOf(third.id()) == ScanStatus.COMPLETED, "forced scan completed");
        verify(domainCheckService, times(200)).analyze(anyString());
    }

    @Test
    @DisplayName("Shared cache: a check older than the validity period is done again")
    void testCacheExpiry(@Autowired DomainCheckRepository cache, @Autowired DomainChecker checker) {
        Instant old = Instant.now().minus(Duration.ofHours(25));
        cache.save(new DomainCheck("expired-test.com", "expired-test.com", DomainStatus.SAFE, 200, TlsStatus.VALID,
                "Old", null, null, null, null, null, old));
        cache.save(new DomainCheck("fresh-test.com", "fresh-test.com", DomainStatus.SAFE, 200, TlsStatus.VALID,
                "Fresh", null, null, null, null, null, Instant.now()));

        assertEquals("Fresh", checker.check("fresh-test.com", false).title());
        assertEquals("Title", checker.check("expired-test.com", false).title());
        verify(domainCheckService, never()).analyze("fresh-test.com");
        verify(domainCheckService, times(1)).analyze("expired-test.com");
        assertEquals("Title", cache.find("expired-test.com").orElseThrow().title(), "the cache is updated");
    }
}
