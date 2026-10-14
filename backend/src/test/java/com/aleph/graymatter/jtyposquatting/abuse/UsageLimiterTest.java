package com.aleph.graymatter.jtyposquatting.abuse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests des quotas d'analyses par utilisateur.
 */
class UsageLimiterTest {

    /** Horloge réglable pour simuler le passage du temps. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-14T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    @DisplayName("Le nombre d'analyses simultanées est limité, et libéré à la fermeture du permis")
    void testConcurrentLimit() {
        UsageLimiter limiter = new UsageLimiter(2, 100, new MutableClock());
        UsageLimiter.ScanPermit first = limiter.acquireScan("alice");
        limiter.acquireScan("alice");

        QuotaExceededException e = assertThrows(QuotaExceededException.class, () -> limiter.acquireScan("alice"));
        assertTrue(e.getMessage().contains("simultaneous"));
        // Les autres utilisateurs ne sont pas affectés
        limiter.acquireScan("bob");

        first.close();
        first.close(); // sans effet la seconde fois
        limiter.acquireScan("alice");
        assertThrows(QuotaExceededException.class, () -> limiter.acquireScan("alice"));
    }

    @Test
    @DisplayName("Le quota journalier est glissant sur 24 heures")
    void testDailyLimit() {
        MutableClock clock = new MutableClock();
        UsageLimiter limiter = new UsageLimiter(10, 3, clock);
        for (int i = 0; i < 3; i++) {
            limiter.acquireScan("alice").close();
            clock.advance(Duration.ofHours(1));
        }

        QuotaExceededException e = assertThrows(QuotaExceededException.class, () -> limiter.acquireScan("alice"));
        assertTrue(e.getMessage().contains("Daily"));
        // La première analyse expire 24 h après son lancement, soit dans 21 h
        assertEquals(Duration.ofHours(21), e.getRetryAfter());

        clock.advance(Duration.ofHours(21).plusSeconds(1));
        limiter.acquireScan("alice");
    }
}
