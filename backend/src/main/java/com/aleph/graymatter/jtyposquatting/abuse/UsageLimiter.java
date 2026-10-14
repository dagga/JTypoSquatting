package com.aleph.graymatter.jtyposquatting.abuse;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Per-user scan quotas: number of simultaneous scans and number of scans over a sliding 24-hour window.
 * <p>
 * Counters are kept in memory (per instance) until scans are persisted (plan P2.2).
 */
@Component
public class UsageLimiter {

    private static final Duration WINDOW = Duration.ofHours(24);

    private final int maxConcurrentScans;
    private final int scansPerDay;
    private final Clock clock;
    private final Cache<String, Usage> usages = CacheBuilder.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(WINDOW.toHours(), TimeUnit.HOURS)
            .build();

    @Autowired
    public UsageLimiter(@Value("${jtypo.quota.max-concurrent-scans:2}") int maxConcurrentScans,
                        @Value("${jtypo.quota.scans-per-day:50}") int scansPerDay) {
        this(maxConcurrentScans, scansPerDay, Clock.systemUTC());
    }

    UsageLimiter(int maxConcurrentScans, int scansPerDay, Clock clock) {
        this.maxConcurrentScans = maxConcurrentScans;
        this.scansPerDay = scansPerDay;
        this.clock = clock;
    }

    /**
     * Reserves a scan slot for the user. The returned permit must be closed when the scan ends.
     *
     * @throws QuotaExceededException if a quota is reached
     */
    public ScanPermit acquireScan(String userKey) {
        Usage usage;
        try {
            usage = usages.get(userKey, Usage::new);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
        synchronized (usage) {
            Instant now = clock.instant();
            while (!usage.starts.isEmpty() && usage.starts.peekFirst().isBefore(now.minus(WINDOW))) {
                usage.starts.pollFirst();
            }
            if (usage.running >= maxConcurrentScans) {
                throw new QuotaExceededException(
                        "Too many simultaneous scans (maximum " + maxConcurrentScans + ")", Duration.ofSeconds(30));
            }
            if (usage.starts.size() >= scansPerDay) {
                Duration retryAfter = Duration.between(now, usage.starts.peekFirst().plus(WINDOW));
                throw new QuotaExceededException(
                        "Daily scan quota reached (" + scansPerDay + " per 24 hours)", retryAfter);
            }
            usage.running++;
            usage.starts.addLast(now);
        }
        return new ScanPermit(usage);
    }

    private static final class Usage {
        private int running;
        private final Deque<Instant> starts = new ArrayDeque<>();
    }

    /** Releases the simultaneous-scan slot; closing twice has no effect. */
    public static final class ScanPermit implements AutoCloseable {
        private final Usage usage;
        private boolean released;

        private ScanPermit(Usage usage) {
            this.usage = usage;
        }

        @Override
        public void close() {
            synchronized (usage) {
                if (!released) {
                    released = true;
                    usage.running--;
                }
            }
        }
    }
}
