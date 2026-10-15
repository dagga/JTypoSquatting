package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;
import com.aleph.graymatter.jtyposquatting.service.DomainCheckService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Complete scan flow on a real MySQL 8.4: repository queries, several users in parallel.
 * Network checks are simulated.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "jtypo.worker.enabled=true",
        "jtypo.worker.poll-interval-ms=100",
        "jtypo.worker.max-concurrent-scans=4",
        "jtypo.quota.max-domains-per-scan=150",
        "jtypo.quota.max-concurrent-scans=5"})
class ScanFlowMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @MockitoBean
    private DomainCheckService domainCheckService;

    @Autowired
    private ScanService scanService;

    @Autowired
    private ScanRepository scans;

    @Autowired
    private ScanResultRepository results;

    @Test
    @DisplayName("Several users in parallel: complete scans, isolated results, no duplicates")
    void testConcurrentUsers() throws Exception {
        when(domainCheckService.analyze(anyString())).thenAnswer(invocation -> {
            String domain = invocation.getArgument(0);
            DomainResultDTO result = new DomainResultDTO(domain, DomainStatus.SAFE, "Title é ✓", "FRENCH", "", 200,
                    null, "", Map.of("Server", "nginx"));
            result.setTlsStatus(TlsStatus.UNTRUSTED);
            return result;
        });
        List<Scan> created = new ArrayList<>();
        for (int user = 0; user < 3; user++) {
            created.add(scanService.createScan(new ScanUser("mysql-user-" + user, "u" + user, null), "exämple" + user + ".com", false));
        }

        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        for (Scan scan : created) {
            while (scans.findById(scan.id()).orElseThrow().status() != ScanStatus.COMPLETED) {
                assertTrue(Instant.now().isBefore(deadline), "timeout");
                Thread.sleep(100);
            }
        }

        for (Scan scan : created) {
            List<ScanResult> stored = results.findAfter(scan.id(), 0, 1000);
            assertEquals(150, stored.size());
            Set<String> domains = new HashSet<>();
            stored.forEach(r -> assertTrue(domains.add(r.domain())));
            assertEquals("Title é ✓", stored.get(0).title(), "UTF-8 preserved");
            assertEquals(Map.of("Server", "nginx"), stored.get(0).httpHeaders(), "colonne JSON");
            assertEquals(TlsStatus.UNTRUSTED, stored.get(0).tlsStatus());
            assertTrue(stored.get(0).id() < stored.get(149).id(), "event order");
        }
        assertEquals(1, scanService.listScans(new ScanUser("mysql-user-0", "u0", null), 50).size());
        assertEquals(com.aleph.graymatter.jtyposquatting.persistence.DomainKeys.toKey("exämple0.com"), created.get(0).baseDomain());
        assertTrue(created.get(0).baseDomain().startsWith("xn--"));
        assertFalse(results.isScreenshotVisibleTo("screenshots/none.png", "mysql-user-0"));
    }
}
