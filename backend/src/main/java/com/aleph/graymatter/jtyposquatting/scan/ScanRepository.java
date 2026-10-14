package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.persistence.DomainKeys;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Access to the scans table, which is also the job queue.
 * <p>
 * Workers claim a job with a conditional UPDATE: only one worker can move a given scan from PENDING
 * (or RUNNING with an expired heartbeat) to RUNNING, on MySQL as on H2.
 */
@Repository
public class ScanRepository {

    private static final RowMapper<Scan> MAPPER = (rs, rowNum) -> new Scan(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("base_domain"),
            ScanStatus.valueOf(rs.getString("status")),
            rs.getBoolean("force_refresh"),
            rs.getInt("total_domains"),
            rs.getInt("checked_domains"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("started_at")),
            toInstant(rs.getTimestamp("finished_at")),
            rs.getString("error"));

    private final JdbcTemplate jdbc;

    public ScanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Scan scan) {
        jdbc.update("""
                INSERT INTO scans (id, user_id, base_domain, base_domain_unicode, status, force_refresh, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, scan.id(), scan.userId(), scan.baseDomain(), DomainKeys.toUnicode(scan.baseDomain()),
                scan.status().name(), scan.forceRefresh(), Timestamp.from(scan.createdAt()));
    }

    public Optional<Scan> findById(String id) {
        return jdbc.query("SELECT * FROM scans WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<Scan> findByUser(String userId, int limit) {
        return jdbc.query("SELECT * FROM scans WHERE user_id = ? ORDER BY created_at DESC LIMIT ?", MAPPER, userId, limit);
    }

    public int countActiveByUser(String userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM scans WHERE user_id = ? AND status IN ('PENDING', 'RUNNING')", Integer.class, userId);
    }

    public int countCreatedSince(String userId, Instant since) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM scans WHERE user_id = ? AND created_at >= ?", Integer.class, userId, Timestamp.from(since));
    }

    public Optional<Instant> oldestCreatedSince(String userId, Instant since) {
        Timestamp oldest = jdbc.queryForObject(
                "SELECT MIN(created_at) FROM scans WHERE user_id = ? AND created_at >= ?", Timestamp.class, userId, Timestamp.from(since));
        return Optional.ofNullable(toInstant(oldest));
    }

    /** Pending scans, and running scans whose worker stopped sending heartbeats. */
    public List<String> findClaimable(Instant staleBefore, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM scans
                WHERE status = 'PENDING' OR (status = 'RUNNING' AND heartbeat_at < ?)
                ORDER BY created_at
                LIMIT ?
                """, String.class, Timestamp.from(staleBefore), limit);
    }

    /** @return true if this worker now owns the scan */
    public boolean claim(String id, String workerId, Instant now, Instant staleBefore) {
        return jdbc.update("""
                UPDATE scans SET status = 'RUNNING', worker_id = ?, heartbeat_at = ?, started_at = COALESCE(started_at, ?)
                WHERE id = ? AND (status = 'PENDING' OR (status = 'RUNNING' AND heartbeat_at < ?))
                """, workerId, Timestamp.from(now), Timestamp.from(now), id, Timestamp.from(staleBefore)) == 1;
    }

    /** @return false if the scan was cancelled or taken over by another worker */
    public boolean heartbeat(String id, String workerId, int checkedDomains, Instant now) {
        return jdbc.update("""
                UPDATE scans SET heartbeat_at = ?, checked_domains = ?
                WHERE id = ? AND worker_id = ? AND status = 'RUNNING'
                """, Timestamp.from(now), checkedDomains, id, workerId) == 1;
    }

    public void setTotal(String id, String workerId, int totalDomains) {
        jdbc.update("UPDATE scans SET total_domains = ? WHERE id = ? AND worker_id = ?", totalDomains, id, workerId);
    }

    public void finish(String id, String workerId, ScanStatus status, int checkedDomains, String error, Instant now) {
        jdbc.update("""
                UPDATE scans SET status = ?, checked_domains = ?, error = ?, finished_at = ?
                WHERE id = ? AND worker_id = ? AND status = 'RUNNING'
                """, status.name(), checkedDomains, error, Timestamp.from(now), id, workerId);
    }

    /** @return true if the scan was active and is now cancelled */
    public boolean cancel(String id, Instant now) {
        return jdbc.update("""
                UPDATE scans SET status = 'CANCELLED', finished_at = ?
                WHERE id = ? AND status IN ('PENDING', 'RUNNING')
                """, Timestamp.from(now), id) == 1;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
