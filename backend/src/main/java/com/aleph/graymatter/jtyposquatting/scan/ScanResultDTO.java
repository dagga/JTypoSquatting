package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Result of one checked domain as returned by the API.
 *
 * @param domain      Unicode form, for display
 * @param domainAscii ASCII/punycode form, the actual DNS name
 */
public record ScanResultDTO(
        long id,
        String domain,
        String domainAscii,
        DomainStatus status,
        int httpCode,
        TlsStatus tlsStatus,
        String title,
        String language,
        String description,
        String homepageText,
        Map<String, String> httpHeaders,
        String screenshotUrl,
        Instant checkedAt) {

    public static ScanResultDTO of(ScanResult result) {
        return new ScanResultDTO(result.id(), result.domainUnicode(), result.domain(), result.status(),
                result.httpCode(), result.tlsStatus(), result.title(), result.language(), result.description(),
                result.homepageText(), result.httpHeaders(), null, result.checkedAt());
    }
}
