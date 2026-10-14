package com.aleph.graymatter.jtyposquatting.scan;

import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;
import com.aleph.graymatter.jtyposquatting.persistence.DomainKeys;
import com.aleph.graymatter.jtyposquatting.service.DomainCheckService;
import com.aleph.graymatter.jtyposquatting.storage.ScreenshotStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Checks domains through the shared cache: a domain checked recently, by any user, is not checked again.
 * Many scans share variants (same parked domains, same TLD swaps), so this saves most outbound requests.
 */
@Service
public class DomainChecker {

    private final DomainCheckService domainCheckService;
    private final DomainCheckRepository cache;
    private final ScreenshotStore screenshots;
    private final Duration ttl;
    private final Clock clock = Clock.systemUTC();

    public DomainChecker(DomainCheckService domainCheckService, DomainCheckRepository cache, ScreenshotStore screenshots,
                         @Value("${jtypo.cache.ttl-hours:24}") long ttlHours) {
        this.domainCheckService = domainCheckService;
        this.cache = cache;
        this.screenshots = screenshots;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * @param domain       ASCII/punycode form
     * @param forceRefresh ignore the cached check
     */
    public DomainCheck check(String domain, boolean forceRefresh) {
        if (!forceRefresh) {
            Optional<DomainCheck> cached = cache.find(domain);
            if (cached.isPresent() && cached.get().checkedAt().isAfter(clock.instant().minus(ttl))) {
                return cached.get();
            }
        }
        DomainResultDTO result = domainCheckService.analyze(domain);
        byte[] png = result.getScreenshot();
        String screenshotKey = png != null && png.length > 0 ? screenshots.put(png) : null;
        DomainCheck check = toCheck(domain, result, screenshotKey, clock.instant());
        cache.save(check);
        return check;
    }

    private static DomainCheck toCheck(String domain, DomainResultDTO result, String screenshotKey, Instant now) {
        DomainStatus status = result.getStatus() != null ? result.getStatus() : DomainStatus.UNKNOWN;
        TlsStatus tlsStatus = result.getTlsStatus() != null ? result.getTlsStatus() : TlsStatus.NONE;
        return new DomainCheck(domain, DomainKeys.toUnicode(domain), status, result.getHttpCode(), tlsStatus,
                result.getTitle(), result.getLanguage(), result.getDescription(), result.getHomepageText(),
                result.getHttpHeaders(), screenshotKey, now);
    }
}
