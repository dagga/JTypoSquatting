package com.aleph.graymatter.jtyposquatting.retention;

import com.aleph.graymatter.jtyposquatting.storage.ScreenshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Purge des données anciennes : analyses terminées, cache, captures devenues orphelines.
 */
@SpringBootTest(properties = "jtypo.storage.directory=${java.io.tmpdir}/jtypo-retention-${random.uuid}")
@ActiveProfiles("test")
class RetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ScreenshotStore store;

    @Autowired
    private MeterRegistry meters;

    private void scan(String id, String status, Duration age) {
        jdbc.update("INSERT INTO scans (id, user_id, base_domain, base_domain_unicode, status, created_at) VALUES (?, 'retention-user', 'example.com', 'example.com', ?, ?)",
                id, status, Timestamp.from(NOW.minus(age)));
    }

    private void result(String scanId, String domain, String screenshotKey) {
        jdbc.update("INSERT INTO scan_results (scan_id, domain, domain_unicode, status, http_code, tls_status, screenshot_key, checked_at) VALUES (?, ?, ?, 'SAFE', 200, 'VALID', ?, ?)",
                scanId, domain, domain, screenshotKey, Timestamp.from(NOW));
    }

    private void check(String domain, String screenshotKey, Duration age) {
        jdbc.update("INSERT INTO domain_checks (domain, domain_unicode, status, http_code, tls_status, screenshot_key, checked_at) VALUES (?, ?, 'SAFE', 200, 'VALID', ?, ?)",
                domain, domain, screenshotKey, Timestamp.from(NOW.minus(age)));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("Seules les données expirées et les captures plus référencées sont supprimées")
    void testPurge() {
        jdbc.update("INSERT INTO users (id, username) VALUES ('retention-user', 'r')");
        String shared = store.put(new byte[]{1, 2, 3});
        String onlyInOldScan = store.put(new byte[]{4, 5, 6});
        String onlyInOldCheck = store.put(new byte[]{7, 8, 9});

        scan("old-done", "COMPLETED", Duration.ofDays(100));
        result("old-done", "a.example", shared);
        result("old-done", "b.example", onlyInOldScan);
        scan("old-running", "RUNNING", Duration.ofDays(100));
        result("old-running", "c.example", null);
        scan("recent", "COMPLETED", Duration.ofDays(10));
        result("recent", "a.example", shared);
        check("old-check.example", onlyInOldCheck, Duration.ofDays(40));
        check("fresh-check.example", shared, Duration.ofDays(1));

        new RetentionService(jdbc, store, meters, Duration.ofDays(90), Duration.ofDays(30), 1000,
                Clock.fixed(NOW, ZoneOffset.UTC)).purge();

        assertEquals(0, count("SELECT COUNT(*) FROM scans WHERE id = 'old-done'"));
        assertEquals(0, count("SELECT COUNT(*) FROM scan_results WHERE scan_id = 'old-done'"), "résultats supprimés en cascade");
        assertEquals(1, count("SELECT COUNT(*) FROM scans WHERE id = 'old-running'"), "une analyse en cours n'est jamais purgée");
        assertEquals(1, count("SELECT COUNT(*) FROM scans WHERE id = 'recent'"));
        assertEquals(0, count("SELECT COUNT(*) FROM domain_checks WHERE domain = 'old-check.example'"));
        assertEquals(1, count("SELECT COUNT(*) FROM domain_checks WHERE domain = 'fresh-check.example'"));

        assertTrue(store.get(shared).isPresent(), "capture encore référencée : conservée");
        assertTrue(store.get(onlyInOldScan).isEmpty(), "capture orpheline : supprimée");
        assertTrue(store.get(onlyInOldCheck).isEmpty(), "capture orpheline : supprimée");
    }
}
