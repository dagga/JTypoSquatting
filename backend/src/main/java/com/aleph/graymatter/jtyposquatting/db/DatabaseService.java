package com.aleph.graymatter.jtyposquatting.db;

import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import org.h2.tools.Server;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class DatabaseService {
    private Connection connection;
    private Server h2Server;

    @PostConstruct
    public void init() throws SQLException {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("H2 driver not found", e);
        }

        // Use persistent database by default
        connection = DriverManager.getConnection("jdbc:h2:file:./typosquatting_db", "sa", "");
        h2Server = null;

        initializeTable();
        
        // Clear database at startup to ensure clean state
        deleteAll();
    }

    @PreDestroy
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            // ignore
        }
    }

    private void initializeTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS domain_page_data (
                domain VARCHAR(255) PRIMARY KEY,
                html_content TEXT,
                text_content TEXT,
                meta_description TEXT,
                meta_keywords VARCHAR(1000),
                meta_author VARCHAR(500),
                meta_og_title VARCHAR(500),
                meta_og_description VARCHAR(1000),
                detected_language VARCHAR(10),
                timestamp BIGINT,
                http_code INTEGER,
                http_headers TEXT,
                screenshot BLOB
            )
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void save(DomainPageDTO data) throws SQLException {
        String sql = """
            MERGE INTO domain_page_data (domain, html_content, text_content, meta_description,
                meta_keywords, meta_author, meta_og_title, meta_og_description,
                detected_language, timestamp, http_code, http_headers, screenshot)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, data.getDomain());
            ps.setString(2, data.getHtmlContent());
            ps.setString(3, data.getTextContent());
            ps.setString(4, data.getMetaDescription());
            ps.setString(5, data.getMetaKeywords());
            ps.setString(6, data.getMetaAuthor());
            ps.setString(7, data.getMetaOgTitle());
            ps.setString(8, data.getMetaOgDescription());
            ps.setString(9, data.getDetectedLanguage());
            ps.setLong(10, data.getTimestamp());
            ps.setInt(11, data.getHttpCode());
            ps.setString(12, data.getHttpHeaders() != null ? new com.google.gson.Gson().toJson(data.getHttpHeaders()) : null);
            ps.setBytes(13, data.getScreenshot());
            ps.executeUpdate();
        }
    }
    public Optional<DomainPageDTO> findByDomain(String domain) throws SQLException {
        String sql = "SELECT * FROM domain_page_data WHERE domain = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, domain);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToDomainPageData(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<DomainPageDTO> findAll() throws SQLException {
        List<DomainPageDTO> results = new ArrayList<>();
        String sql = "SELECT * FROM domain_page_data";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapResultSetToDomainPageData(rs));
            }
        }
        return results;
    }

    public void deleteByDomain(String domain) throws SQLException {
        String sql = "DELETE FROM domain_page_data WHERE domain = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, domain);
            ps.executeUpdate();
        }
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM domain_page_data";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public void deleteAll() throws SQLException {
        String sql = "DELETE FROM domain_page_data";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
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
                com.google.gson.Gson gson = new com.google.gson.Gson();
                @SuppressWarnings("unchecked")
                java.util.Map<String, String> headers = gson.fromJson(headersJson, java.util.Map.class);
                data.setHttpHeaders(headers);
            } catch (Exception ignored) {}
        }
        
        data.setScreenshot(rs.getBytes("screenshot"));
        return data;
    }
}
