package com.aleph.graymatter.jtyposquatting.account;

import com.aleph.graymatter.jtyposquatting.scan.ScanDTO;
import com.aleph.graymatter.jtyposquatting.scan.ScanRepository;
import com.aleph.graymatter.jtyposquatting.scan.ScanResultDTO;
import com.aleph.graymatter.jtyposquatting.scan.ScanResultRepository;
import com.aleph.graymatter.jtyposquatting.scan.ScanUser;
import com.aleph.graymatter.jtyposquatting.storage.ScreenshotGarbageCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rights of the data subject (GDPR): access and portability (export), erasure (account deletion).
 */
@Service
public class AccountService {
    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
    private static final int MAX_RESULTS_PER_SCAN = 100_000;

    private final JdbcTemplate jdbc;
    private final ScanRepository scans;
    private final ScanResultRepository results;
    private final ScreenshotGarbageCollector garbageCollector;

    public AccountService(JdbcTemplate jdbc, ScanRepository scans, ScanResultRepository results,
                          ScreenshotGarbageCollector garbageCollector) {
        this.jdbc = jdbc;
        this.scans = scans;
        this.results = results;
        this.garbageCollector = garbageCollector;
    }

    /** Everything stored about the user, in a portable format. */
    public Map<String, Object> export(ScanUser user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        jdbc.query("SELECT * FROM users WHERE id = ?", rs -> {
            profile.put("id", rs.getString("id"));
            profile.put("username", rs.getString("username"));
            profile.put("email", rs.getString("email"));
            profile.put("createdAt", rs.getTimestamp("created_at").toInstant());
            profile.put("lastSeenAt", rs.getTimestamp("last_seen_at").toInstant());
        }, user.id());

        List<Map<String, Object>> exportedScans = new ArrayList<>();
        for (var scan : scans.findByUser(user.id(), Integer.MAX_VALUE)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("scan", ScanDTO.of(scan));
            entry.put("results", results.findAfter(scan.id(), 0, MAX_RESULTS_PER_SCAN).stream().map(ScanResultDTO::of).toList());
            exportedScans.add(entry);
        }

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now());
        export.put("user", profile);
        export.put("scans", exportedScans);
        return export;
    }

    /**
     * Deletes the user, their scans and results (database cascade), and the screenshots that no other
     * scan uses. Cached checks are not personal data (shared, public sites) and are kept.
     */
    public void delete(ScanUser user) {
        List<String> screenshots = jdbc.queryForList("""
                SELECT DISTINCT r.screenshot_key FROM scan_results r JOIN scans s ON s.id = r.scan_id
                WHERE s.user_id = ? AND r.screenshot_key IS NOT NULL
                """, String.class, user.id());
        int deletedScans = jdbc.update("DELETE FROM scans WHERE user_id = ?", user.id());
        jdbc.update("DELETE FROM users WHERE id = ?", user.id());
        int deletedScreenshots = garbageCollector.deleteUnreferenced(screenshots);
        logger.info("Account {} deleted: {} scans, {} screenshots", user.id(), deletedScans, deletedScreenshots);
    }
}
