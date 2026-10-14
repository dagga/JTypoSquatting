package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.persistence.DomainKeys;

import java.time.Instant;

/**
 * A scan of the typo variants of one domain, owned by one user.
 */
public record Scan(
        String id,
        String userId,
        String baseDomain,
        ScanStatus status,
        boolean forceRefresh,
        int totalDomains,
        int checkedDomains,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String error) {

    /** Unicode form of the scanned domain, for display. */
    public String baseDomainUnicode() {
        return DomainKeys.toUnicode(baseDomain);
    }
}
