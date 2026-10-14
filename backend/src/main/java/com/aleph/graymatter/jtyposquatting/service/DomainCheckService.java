package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.db.DatabaseService;
import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import com.aleph.graymatter.jtyposquatting.dto.DomainStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

@Service
public class DomainCheckService {

    private static final Logger logger = LoggerFactory.getLogger(DomainCheckService.class);

    private final DatabaseService databaseService;
    private final PageAnalyzer pageAnalyzer;

    // Caps outbound checks for the whole instance: a scan submits thousands of domains at once
    private final Semaphore outboundChecks;

    public DomainCheckService(DatabaseService databaseService, PageAnalyzer pageAnalyzer,
                              @Value("${jtypo.analysis.max-concurrent-checks:64}") int maxConcurrentChecks) {
        this.databaseService = databaseService;
        this.pageAnalyzer = pageAnalyzer;
        this.outboundChecks = new Semaphore(maxConcurrentChecks, true);
    }

    /**
     * Checks a domain and stores the page in the legacy table of the desktop application.
     */
    public DomainResultDTO checkDomain(String domain) {
        return check(domain, true);
    }

    /**
     * Checks a domain without storing anything (web scans persist their own results).
     */
    public DomainResultDTO analyze(String domain) {
        return check(domain, false);
    }

    private DomainResultDTO check(String domain, boolean saveLegacy) {
        try {
            outboundChecks.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DomainResultDTO(domain, DomainStatus.UNREACHABLE, "", "", "Cancelled", 0, null, "", null);
        }
        try {
            DomainPageDTO pageData = pageAnalyzer.analyzePage(domain);

            // If we got an HTTP code > 0, the domain is "Alive"
            if (pageData != null && pageData.getHttpCode() > 0) {
                if (saveLegacy) {
                    try {
                        databaseService.save(pageData);
                    } catch (Exception e) {
                        logger.warn("Failed to save {}: {}", domain, e.getMessage());
                    }
                }

                // Truncate long fields for DTO
                String title = pageData.getTitle() != null ? pageData.getTitle() : "";
                if (title.length() > 100) {
                    title = title.substring(0, 97) + "...";
                }
                String description = pageData.getMetaDescription() != null ? pageData.getMetaDescription() : "";
                if (description.length() > 300) {
                    description = description.substring(0, 297) + "...";
                }

                // Determine status based on HTTP code
                // HTTP 200 -> Suspicious (Red)
                // Other HTTP codes -> Safe (Green)
                DomainStatus domainStatus = (pageData.getHttpCode() == 200) ? DomainStatus.SUSPICIOUS : DomainStatus.SAFE;

                byte[] screenshot = pageData.getScreenshot();
                logger.debug("Sending result for {} with screenshot: {} bytes", domain, screenshot != null ? screenshot.length : 0);

                DomainResultDTO result = new DomainResultDTO(
                        domain,
                        domainStatus,
                        title,
                        pageData.getDetectedLanguage(),
                        description,
                        pageData.getHttpCode(),
                        screenshot,
                        pageData.getHomepageText(),
                        pageData.getHttpHeaders()
                );
                result.setTlsStatus(pageData.getTlsStatus());
                return result;
            } else {
                // No HTTP code (timeout, unknown host, etc.) -> Unreachable (should be removed from list)
                return new DomainResultDTO(
                        domain,
                        DomainStatus.UNREACHABLE,
                        "",
                        "",
                        "Domain not reachable",
                        0,
                        null,
                        "",
                        null
                );
            }
        } catch (Exception e) {
            logger.warn("Error analyzing {}: {}", domain, e.getMessage());
            // Treat exceptions as "Unreachable"
            return new DomainResultDTO(
                    domain,
                    DomainStatus.UNREACHABLE,
                    "",
                    "",
                    "Error: " + e.getMessage(),
                    0,
                    null,
                    "",
                    null
            );
        } finally {
            outboundChecks.release();
        }
    }
}
