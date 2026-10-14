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
import java.util.Map;
import java.util.Optional;

/**
 * Latest check of each domain, shared by all users (table domain_checks).
 */
@Repository
public class DomainCheckRepository {

    private static final Gson GSON = new Gson();
    private static final Type HEADERS_TYPE = new TypeToken<Map<String, String>>() { }.getType();

    private static final RowMapper<DomainCheck> MAPPER = (rs, rowNum) -> new DomainCheck(
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

    public DomainCheckRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DomainCheck> find(String domain) {
        return jdbc.query("SELECT * FROM domain_checks WHERE domain = ?", MAPPER, domain).stream().findFirst();
    }

    /** Portable upsert (H2 and MySQL). */
    public void save(DomainCheck check) {
        Object[] values = {
                check.domainUnicode(), check.status().name(), check.httpCode(), check.tlsStatus().name(),
                truncate(check.title(), 500), truncate(check.language(), 32), truncate(check.description(), 1000),
                truncate(check.homepageText(), 1000),
                check.httpHeaders() != null ? GSON.toJson(check.httpHeaders()) : null,
                check.screenshotKey(), Timestamp.from(check.checkedAt()), check.domain()
        };
        String update = """
                UPDATE domain_checks SET domain_unicode = ?, status = ?, http_code = ?, tls_status = ?, title = ?,
                    language = ?, description = ?, homepage_text = ?, http_headers = ?, screenshot_key = ?, checked_at = ?
                WHERE domain = ?
                """;
        if (jdbc.update(update, values) == 0) {
            try {
                jdbc.update("""
                        INSERT INTO domain_checks (domain_unicode, status, http_code, tls_status, title, language,
                            description, homepage_text, http_headers, screenshot_key, checked_at, domain)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, values);
            } catch (DuplicateKeyException e) {
                // Inserted concurrently by another worker: keep the latest
                jdbc.update(update, values);
            }
        }
    }

    static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
