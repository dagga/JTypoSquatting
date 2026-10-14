package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.abuse.QuotaExceededException;
import com.aleph.graymatter.jtyposquatting.net.DomainName;
import com.aleph.graymatter.jtyposquatting.persistence.DomainKeys;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Creation and access control of scans. Quotas are computed from the scans table, so they hold
 * across API instances and restarts.
 */
@Service
public class ScanService {

    static final Duration QUOTA_WINDOW = Duration.ofHours(24);

    private final ScanRepository scans;
    private final UserRepository users;
    private final int maxConcurrentScans;
    private final int scansPerDay;
    private final Clock clock;
    private final MeterRegistry meters;

    public ScanService(ScanRepository scans, UserRepository users, MeterRegistry meters,
                       @Value("${jtypo.quota.max-concurrent-scans:2}") int maxConcurrentScans,
                       @Value("${jtypo.quota.scans-per-day:50}") int scansPerDay) {
        this.scans = scans;
        this.users = users;
        this.meters = meters;
        this.maxConcurrentScans = maxConcurrentScans;
        this.scansPerDay = scansPerDay;
        this.clock = Clock.systemUTC();
    }

    /**
     * Queues a scan for a worker.
     *
     * @throws InvalidDomainException if the domain name is not valid
     * @throws QuotaExceededException if the user has too many active scans or reached the daily quota
     */
    @Transactional
    public Scan createScan(ScanUser user, String baseDomain, boolean forceRefresh) throws InvalidDomainException {
        String key = toValidKey(baseDomain);
        users.upsert(user.id(), user.username(), user.email());

        Instant now = clock.instant();
        if (scans.countActiveByUser(user.id()) >= maxConcurrentScans) {
            meters.counter("jtypo.quota.rejections", "type", "concurrent").increment();
            throw new QuotaExceededException(
                    "Too many simultaneous scans (maximum " + maxConcurrentScans + ")", Duration.ofSeconds(30));
        }
        Instant windowStart = now.minus(QUOTA_WINDOW);
        if (scans.countCreatedSince(user.id(), windowStart) >= scansPerDay) {
            meters.counter("jtypo.quota.rejections", "type", "daily").increment();
            Duration retryAfter = scans.oldestCreatedSince(user.id(), windowStart)
                    .map(oldest -> Duration.between(now, oldest.plus(QUOTA_WINDOW)))
                    .orElse(QUOTA_WINDOW);
            throw new QuotaExceededException("Daily scan quota reached (" + scansPerDay + " per 24 hours)", retryAfter);
        }

        Scan scan = new Scan(UUID.randomUUID().toString(), user.id(), key, ScanStatus.PENDING, forceRefresh,
                0, 0, now, null, null, null);
        scans.insert(scan);
        meters.counter("jtypo.scans.submitted").increment();
        return scan;
    }

    /**
     * @throws ScanNotFoundException if the scan does not exist
     * @throws ScanAccessDeniedException if the scan belongs to another user
     */
    public Scan getScan(ScanUser user, String scanId) {
        Scan scan = scans.findById(scanId).orElseThrow(() -> new ScanNotFoundException(scanId));
        if (!scan.userId().equals(user.id())) {
            throw new ScanAccessDeniedException(scanId);
        }
        return scan;
    }

    /** @return true if the scan was active and is now cancelled; the worker stops at its next heartbeat */
    public boolean cancelScan(ScanUser user, String scanId) {
        getScan(user, scanId);
        return scans.cancel(scanId, clock.instant());
    }

    public List<Scan> listScans(ScanUser user, int limit) {
        return scans.findByUser(user.id(), limit);
    }

    /** Validates the domain and returns its stored form (punycode). */
    static String toValidKey(String baseDomain) throws InvalidDomainException {
        if (baseDomain == null || baseDomain.isBlank()) {
            throw new InvalidDomainException("Domain is required");
        }
        String key;
        try {
            key = DomainKeys.toKey(baseDomain);
        } catch (IllegalArgumentException e) {
            throw new InvalidDomainException(baseDomain + " is not valid");
        }
        new DomainName(key);
        return key;
    }
}
