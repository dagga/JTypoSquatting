package com.aleph.graymatter.jtyposquatting.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Réclamation concurrente d'une analyse par plusieurs workers sur MySQL : un seul l'obtient.
 * Aucun worker de fond dans ce contexte, seuls les threads du test se disputent l'analyse.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "jtypo.worker.enabled=false")
class ScanClaimMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ScanService scanService;

    @Autowired
    private ScanRepository scans;

    @Autowired
    private JdbcTemplate jdbc;

    private int race(String scanId, int workers) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> claims = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            String worker = "worker-" + i;
            claims.add(() -> {
                start.await();
                Instant now = Instant.now();
                return scans.claim(scanId, worker, now, now.minus(Duration.ofMinutes(2)));
            });
        }
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> claim : claims) {
            futures.add(pool.submit(claim));
        }
        start.countDown();
        int winners = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) winners++;
        }
        pool.shutdown();
        return winners;
    }

    @Test
    @DisplayName("Une analyse en attente est obtenue par un seul des workers concurrents")
    void testPendingClaimRace() throws Exception {
        Scan scan = scanService.createScan(new ScanUser("race-user", "r", null), "race-test.com", false);

        assertEquals(1, race(scan.id(), 8));
        assertEquals(ScanStatus.RUNNING, scans.findById(scan.id()).orElseThrow().status());
    }

    @Test
    @DisplayName("Une analyse abandonnée est reprise par un seul worker ; une analyse vivante ne l'est pas")
    void testStaleClaimRace() throws Exception {
        Scan stale = scanService.createScan(new ScanUser("stale-user", "s", null), "stale-test.com", false);
        jdbc.update("UPDATE scans SET status = 'RUNNING', worker_id = 'dead', heartbeat_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(10))), stale.id());
        assertEquals(1, race(stale.id(), 8));

        Scan alive = scanService.createScan(new ScanUser("alive-user", "a", null), "alive-test.com", false);
        jdbc.update("UPDATE scans SET status = 'RUNNING', worker_id = 'alive', heartbeat_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), alive.id());
        assertEquals(0, race(alive.id(), 8));
    }
}
