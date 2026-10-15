package com.aleph.graymatter.jtyposquatting.scan;

import java.time.Instant;

/**
 * Scan as returned by the API.
 */
public record ScanDTO(
        String id,
        String baseDomain,
        ScanStatus status,
        int totalDomains,
        int checkedDomains,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String error) {

    public static ScanDTO of(Scan scan) {
        return new ScanDTO(scan.id(), scan.baseDomainUnicode(), scan.status(), scan.totalDomains(),
                scan.checkedDomains(), scan.createdAt(), scan.startedAt(), scan.finishedAt(), scan.error());
    }
}
