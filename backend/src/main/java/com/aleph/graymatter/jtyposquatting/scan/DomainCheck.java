package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Outcome of the check of one domain, as kept in the shared cache.
 *
 * @param domain        ASCII/punycode form
 * @param screenshotKey key of the screenshot in the object storage, or null
 */
public record DomainCheck(
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

    ScanResult toScanResult(String scanId) {
        return new ScanResult(0, scanId, domain, domainUnicode, status, httpCode, tlsStatus, title, language,
                description, homepageText, httpHeaders, screenshotKey, checkedAt);
    }
}
