package com.aleph.graymatter.jtyposquatting.db;

import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class DatabaseService {
    private final com.google.gson.Gson gson = new com.google.gson.Gson();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Legacy storage of the desktop application. The table is created by Flyway
     * (V3__legacy_domain_page_data) and emptied at startup, as before.
     */
    @PostConstruct
    public void init() {
        deleteAll();
    }

    @PreDestroy
    public void close() {
        // No persistent connection held
    }

    public void save(DomainPageDTO data) {
        // Portable upsert (H2 and MySQL)
        Object[] values = {
            data.getHtmlContent(),
            data.getTextContent(),
            data.getMetaDescription(),
            data.getMetaKeywords(),
            data.getMetaAuthor(),
            data.getMetaOgTitle(),
            data.getMetaOgDescription(),
            data.getDetectedLanguage(),
            data.getTimestamp(),
            data.getHttpCode(),
            data.getHttpHeaders() != null ? gson.toJson(data.getHttpHeaders()) : null,
            data.getScreenshot(),
            data.getDomain()
        };
        int updated = jdbcTemplate.update("""
            UPDATE domain_page_data SET html_content = ?, text_content = ?, meta_description = ?,
                meta_keywords = ?, meta_author = ?, meta_og_title = ?, meta_og_description = ?,
                detected_language = ?, timestamp = ?, http_code = ?, http_headers = ?, screenshot = ?
            WHERE domain = ?
            """, values);
        if (updated == 0) {
            jdbcTemplate.update("""
                INSERT INTO domain_page_data (html_content, text_content, meta_description,
                    meta_keywords, meta_author, meta_og_title, meta_og_description,
                    detected_language, timestamp, http_code, http_headers, screenshot, domain)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, values);
        }
    }

    public Optional<DomainPageDTO> findByDomain(String domain) {
        String sql = "SELECT * FROM domain_page_data WHERE domain = ?";
        List<DomainPageDTO> results = jdbcTemplate.query(sql, (rs, rowNum) -> mapResultSetToDomainPageData(rs), domain);
        return results.stream().findFirst();
    }

    public List<DomainPageDTO> findAll() {
        String sql = "SELECT * FROM domain_page_data";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapResultSetToDomainPageData(rs));
    }

    public void deleteByDomain(String domain) {
        String sql = "DELETE FROM domain_page_data WHERE domain = ?";
        jdbcTemplate.update(sql, domain);
    }

    public int count() {
        String sql = "SELECT COUNT(*) FROM domain_page_data";
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    public void deleteAll() {
        String sql = "DELETE FROM domain_page_data";
        jdbcTemplate.update(sql);
    }

    private DomainPageDTO mapResultSetToDomainPageData(ResultSet rs) throws SQLException {
        DomainPageDTO data = new DomainPageDTO();
        data.setDomain(rs.getString("domain"));
        data.setHtmlContent(rs.getString("html_content"));
        data.setTextContent(rs.getString("text_content"));
        data.setMetaDescription(rs.getString("meta_description"));
        data.setMetaKeywords(rs.getString("meta_keywords"));
        data.setMetaAuthor(rs.getString("meta_author"));
        data.setMetaOgTitle(rs.getString("meta_og_title"));
        data.setMetaOgDescription(rs.getString("meta_og_description"));
        data.setDetectedLanguage(rs.getString("detected_language"));
        data.setTimestamp(rs.getLong("timestamp"));
        data.setHttpCode(rs.getInt("http_code"));
        
        String headersJson = rs.getString("http_headers");
        if (headersJson != null && !headersJson.trim().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> headers = gson.fromJson(headersJson, java.util.Map.class);
                data.setHttpHeaders(headers);
            } catch (Exception ignored) {}
        }
        
        data.setScreenshot(rs.getBytes("screenshot"));
        return data;
    }
}
