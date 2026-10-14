package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Result of the check of one generated domain within a scan.
 *
 * @param id            order of the result in the scan, used as SSE event id
 * @param domain        ASCII/punycode form
 * @param screenshotKey key of the screenshot in the object storage, or null
 */
public record ScanResult(
        long id,
        String scanId,
        String domain,
        String domainUnicode,
        DomainStatus status,
        int httpCode,
        TlsStatus tlsStatus,
        String title,
        String language,
        String description,
        String homepageText,
        Map<String, String> httpHeaders,
        String screenshotKey,
        Instant checkedAt) {
}
