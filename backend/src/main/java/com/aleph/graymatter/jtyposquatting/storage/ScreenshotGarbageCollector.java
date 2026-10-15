package com.aleph.graymatter.jtyposquatting.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Deletes screenshots that no scan result nor cached check references any more. Screenshots are shared
 * between scans (content-addressed), so a screenshot can only be deleted once its last reference is gone.
 */
@Component
public class ScreenshotGarbageCollector {
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotGarbageCollector.class);

    private final JdbcTemplate jdbc;
    private final ScreenshotStore store;

    public ScreenshotGarbageCollector(JdbcTemplate jdbc, ScreenshotStore store) {
        this.jdbc = jdbc;
        this.store = store;
    }

    /** @return the number of screenshots deleted among the candidates */
    public int deleteUnreferenced(Collection<String> candidates) {
        int deleted = 0;
        for (String key : candidates) {
            Integer references = jdbc.queryForObject("""
                    SELECT (SELECT COUNT(*) FROM scan_results WHERE screenshot_key = ?)
                         + (SELECT COUNT(*) FROM domain_checks WHERE screenshot_key = ?)
                    """, Integer.class, key, key);
            if (references != null && references == 0) {
                try {
                    store.delete(key);
                    deleted++;
                } catch (RuntimeException e) {
                    logger.warn("Could not delete screenshot {}: {}", key, e.getMessage());
                }
            }
        }
        return deleted;
    }
}
