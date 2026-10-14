package com.aleph.graymatter.jtyposquatting.retention;

import com.aleph.graymatter.jtyposquatting.storage.ScreenshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes data older than the retention periods: finished scans with their results, cached checks,
 * and the screenshots that no remaining row references (screenshots are shared between scans).
 * <p>
 * Runs on worker instances; several workers may run it at the same time, every step is idempotent.
 * Each run deletes a bounded number of scans, to keep transactions small.
 */
@Service
@ConditionalOnProperty(name = "jtypo.worker.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionService {
    private static final Logger logger = LoggerFactory.getLogger(RetentionService.class);
    private static final int BATCH = 200;

    private final JdbcTemplate jdbc;
    private final ScreenshotStore screenshots;
    private final MeterRegistry meters;
    private final Duration scanRetention;
    private final Duration checkRetention;
    private final int maxScansPerRun;
    private final Clock clock;

    public RetentionService(JdbcTemplate jdbc, ScreenshotStore screenshots, MeterRegistry meters,
                            @Value("${jtypo.retention.scan-days:90}") int scanDays,
                            @Value("${jtypo.retention.check-days:30}") int checkDays,
                            @Value("${jtypo.retention.max-scans-per-run:5000}") int maxScansPerRun) {
        this(jdbc, screenshots, meters, Duration.ofDays(scanDays), Duration.ofDays(checkDays), maxScansPerRun, Clock.systemUTC());
    }

    RetentionService(JdbcTemplate jdbc, ScreenshotStore screenshots, MeterRegistry meters, Duration scanRetention,
                     Duration checkRetention, int maxScansPerRun, Clock clock) {
        this.jdbc = jdbc;
        this.screenshots = screenshots;
        this.meters = meters;
        this.scanRetention = scanRetention;
        this.checkRetention = checkRetention;
        this.maxScansPerRun = maxScansPerRun;
        this.clock = clock;
    }

    @Scheduled(cron = "${jtypo.retention.cron:0 30 3 * * *}")
    public void purge() {
        Instant now = clock.instant();
        Set<String> candidateScreenshots = new LinkedHashSet<>();
        int scans = purgeScans(Timestamp.from(now.minus(scanRetention)), candidateScreenshots);
        int checks = purgeChecks(Timestamp.from(now.minus(checkRetention)), candidateScreenshots);
        int files = purgeOrphanScreenshots(candidateScreenshots);
        meters.counter("jtypo.retention.deleted", "type", "scans").increment(scans);
        meters.counter("jtypo.retention.deleted", "type", "checks").increment(checks);
        meters.counter("jtypo.retention.deleted", "type", "screenshots").increment(files);
        logger.info("Retention: {} scans, {} cached checks, {} screenshots deleted", scans, checks, files);
    }

    /** Finished scans created before the limit; their results are deleted by cascade. */
    private int purgeScans(Timestamp before, Set<String> candidateScreenshots) {
        int deleted = 0;
        while (deleted < maxScansPerRun) {
            List<String> ids = jdbc.queryForList("""
                    SELECT id FROM scans WHERE created_at < ? AND status NOT IN ('PENDING', 'RUNNING')
                    ORDER BY created_at LIMIT ?
                    """, String.class, before, BATCH);
            if (ids.isEmpty()) {
                break;
            }
            String in = String.join(",", Collections.nCopies(ids.size(), "?"));
            candidateScreenshots.addAll(jdbc.queryForList(
                    "SELECT DISTINCT screenshot_key FROM scan_results WHERE screenshot_key IS NOT NULL AND scan_id IN (" + in + ")",
                    String.class, ids.toArray()));
            deleted += jdbc.update("DELETE FROM scans WHERE id IN (" + in + ")", ids.toArray());
        }
        return deleted;
    }

    private int purgeChecks(Timestamp before, Set<String> candidateScreenshots) {
        candidateScreenshots.addAll(jdbc.queryForList(
                "SELECT DISTINCT screenshot_key FROM domain_checks WHERE screenshot_key IS NOT NULL AND checked_at < ?",
                String.class, before));
        return jdbc.update("DELETE FROM domain_checks WHERE checked_at < ?", before);
    }

    /** Deletes the screenshots of deleted rows that no other scan result nor cached check references. */
    private int purgeOrphanScreenshots(Set<String> candidates) {
        int deleted = 0;
        for (String key : candidates) {
            Integer references = jdbc.queryForObject("""
                    SELECT (SELECT COUNT(*) FROM scan_results WHERE screenshot_key = ?)
                         + (SELECT COUNT(*) FROM domain_checks WHERE screenshot_key = ?)
                    """, Integer.class, key, key);
            if (references != null && references == 0) {
                try {
                    screenshots.delete(key);
                    deleted++;
                } catch (RuntimeException e) {
                    logger.warn("Could not delete screenshot {}: {}", key, e.getMessage());
                }
            }
        }
        return deleted;
    }
}
