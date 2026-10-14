package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Results of scans. Results are append-only: the auto-increment id gives the order of the SSE events.
 */
@Repository
public class ScanResultRepository {

    private static final Gson GSON = new Gson();
    private static final Type HEADERS_TYPE = new TypeToken<Map<String, String>>() { }.getType();

    private static final RowMapper<ScanResult> MAPPER = (rs, rowNum) -> new ScanResult(
            rs.getLong("id"),
            rs.getString("scan_id"),
            rs.getString("domain"),
            rs.getString("domain_unicode"),
            DomainStatus.valueOf(rs.getString("status")),
            rs.getInt("http_code"),
            TlsStatus.valueOf(rs.getString("tls_status")),
            rs.getString("title"),
            rs.getString("language"),
            rs.getString("description"),
            rs.getString("homepage_text"),
            rs.getString("http_headers") != null ? GSON.fromJson(rs.getString("http_headers"), HEADERS_TYPE) : null,
            rs.getString("screenshot_key"),
            rs.getTimestamp("checked_at").toInstant());

    private final JdbcTemplate jdbc;

    public ScanResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return false if a result already exists for this domain in this scan (resumed scan) */
    public boolean insert(ScanResult result) {
        try {
            jdbc.update("""
                    INSERT INTO scan_results (scan_id, domain, domain_unicode, status, http_code, tls_status, title,
                        language, description, homepage_text, http_headers, screenshot_key, checked_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    result.scanId(), result.domain(), result.domainUnicode(), result.status().name(),
                    result.httpCode(), result.tlsStatus().name(), truncate(result.title(), 500),
                    truncate(result.language(), 32), truncate(result.description(), 1000),
                    truncate(result.homepageText(), 1000),
                    result.httpHeaders() != null ? GSON.toJson(result.httpHeaders()) : null,
                    result.screenshotKey(), Timestamp.from(result.checkedAt()));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /** Results of a scan with an id greater than {@code afterId}, in order. */
    public List<ScanResult> findAfter(String scanId, long afterId, int limit) {
        return jdbc.query("SELECT * FROM scan_results WHERE scan_id = ? AND id > ? ORDER BY id LIMIT ?",
                MAPPER, scanId, afterId, limit);
    }

    public Set<String> findCheckedDomains(String scanId) {
        return new HashSet<>(jdbc.queryForList("SELECT domain FROM scan_results WHERE scan_id = ?", String.class, scanId));
    }

    /** True if one of the user's scans contains this screenshot. */
    public boolean isScreenshotVisibleTo(String screenshotKey, String userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT COUNT(*) > 0 FROM scan_results r JOIN scans s ON s.id = r.scan_id
                WHERE r.screenshot_key = ? AND s.user_id = ?
                """, Boolean.class, screenshotKey, userId));
    }

    public int countByScan(String scanId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM scan_results WHERE scan_id = ?", Integer.class, scanId);
    }

    private static String truncate(String value, int max) {
        return DomainCheckRepository.truncate(value, max);
    }
}
