package com.aleph.graymatter.jtyposquatting.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flyway migrations and schema constraints on a real MySQL 8.4 (skipped without a container engine).
 * <p>
 * With rootless Podman: DOCKER_HOST=unix:///run/user/$UID/podman/podman.sock
 * and TESTCONTAINERS_RYUK_DISABLED=true.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class MySqlSchemaTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private JdbcTemplate jdbc;

    private void insertCheck(String domain) {
        jdbc.update("""
                INSERT INTO domain_checks (domain, domain_unicode, status, http_code, tls_status, checked_at)
                VALUES (?, ?, 'SAFE', 200, 'VALID', ?)
                """, DomainKeys.toKey(domain), domain, Timestamp.from(Instant.now()));
    }

    @Test
    @DisplayName("All migrations are applied on MySQL")
    void testMigrationsApplied() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL", Integer.class);
        assertEquals(4, applied);
        assertEquals("8.4", jdbc.queryForObject("SELECT LEFT(VERSION(), 3)", String.class));
    }

    @Test
    @DisplayName("Homoglyph or case variants are never confused")
    void testDomainCollation() {
        insertCheck("example.com");
        insertCheck("exämple.com");
        insertCheck("еxample.com");
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM domain_checks WHERE domain_unicode LIKE '%xample.com' OR domain_unicode LIKE '%xämple.com'",
                Integer.class));
        // Binary comparison: the ASCII key of another domain does not match
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM domain_checks WHERE domain = 'EXAMPLE.COM'", Integer.class));
        assertThrows(DuplicateKeyException.class, () -> insertCheck("example.com"));
    }

    @Test
    @DisplayName("HTTP sessions are stored in MySQL (Spring Session)")
    void testSessionTables() {
        assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class));
    }
}
