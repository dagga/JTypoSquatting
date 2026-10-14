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
 * Migrations Flyway et contraintes du schéma sur un vrai MySQL 8.4 (ignoré sans moteur de conteneurs).
 * <p>
 * Avec Podman sans privilèges : DOCKER_HOST=unix:///run/user/$UID/podman/podman.sock
 * et TESTCONTAINERS_RYUK_DISABLED=true.
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
    @DisplayName("Toutes les migrations sont appliquées sur MySQL")
    void testMigrationsApplied() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL", Integer.class);
        assertEquals(3, applied);
        assertEquals("8.4", jdbc.queryForObject("SELECT LEFT(VERSION(), 3)", String.class));
    }

    @Test
    @DisplayName("Des variantes homoglyphes ou de casse ne sont jamais confondues")
    void testDomainCollation() {
        insertCheck("example.com");
        insertCheck("exämple.com");
        insertCheck("еxample.com");
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM domain_checks WHERE domain_unicode LIKE '%xample.com' OR domain_unicode LIKE '%xämple.com'",
                Integer.class));
        // Comparaison binaire : la clé ASCII d'un autre domaine ne correspond pas
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM domain_checks WHERE domain = 'EXAMPLE.COM'", Integer.class));
        assertThrows(DuplicateKeyException.class, () -> insertCheck("example.com"));
    }

    @Test
    @DisplayName("Les sessions HTTP sont stockées dans MySQL (Spring Session)")
    void testSessionTables() {
        assertNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class));
    }
}
