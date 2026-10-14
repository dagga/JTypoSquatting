package com.aleph.graymatter.jtyposquatting.scan;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Size of the scan queue, read from the database when metrics are scraped. Every instance reports
 * the same global values: aggregate them with max(), not sum().
 */
@Component
public class ScanQueueMetrics {

    public ScanQueueMetrics(JdbcTemplate jdbc, MeterRegistry meters) {
        for (ScanStatus status : new ScanStatus[]{ScanStatus.PENDING, ScanStatus.RUNNING}) {
            Gauge.builder("jtypo.scans.queue", () -> count(jdbc, status))
                    .tag("status", status.name())
                    .description("Scans waiting for or being processed by a worker")
                    .register(meters);
        }
    }

    private static double count(JdbcTemplate jdbc, ScanStatus status) {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM scans WHERE status = ?", Integer.class, status.name());
            return count != null ? count : 0;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
