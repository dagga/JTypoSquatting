package com.aleph.graymatter.jtyposquatting.scan;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Users known to the service, identified by their OpenID Connect subject.
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates the user on first use and refreshes the profile afterwards (portable upsert). */
    public void upsert(String id, String username, String email) {
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbc.update("UPDATE users SET username = ?, email = ?, last_seen_at = ? WHERE id = ?",
                username, email, now, id);
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO users (id, username, email, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?)",
                        id, username, email, now, now);
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // Created concurrently by another request
            }
        }
    }
}
