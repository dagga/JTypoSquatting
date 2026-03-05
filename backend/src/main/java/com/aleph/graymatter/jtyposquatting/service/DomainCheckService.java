package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.db.DatabaseService;
import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import org.springframework.stereotype.Service;

@Service
public class DomainCheckService {

    private final DatabaseService databaseService;
    private final PageAnalyzer pageAnalyzer;

    public DomainCheckService(DatabaseService databaseService, PageAnalyzer pageAnalyzer) {
        this.databaseService = databaseService;
        this.pageAnalyzer = pageAnalyzer;
    }

    public DomainResultDTO checkDomain(String domain) {
        try {
            DomainPageDTO pageData = pageAnalyzer.analyzePage(domain);

            // If we got an HTTP code > 0, the domain is "Alive"
            if (pageData != null && pageData.getHttpCode() > 0) {
                // Save to database
                try {
                    databaseService.save(pageData);
                } catch (Exception e) {
                    System.err.println("[DomainCheck] Failed to save " + domain + ": " + e.getMessage());
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
                String status = (pageData.getHttpCode() == 200) ? "Suspicious" : "Safe";

                return new DomainResultDTO(
                        domain,
                        status,
                        title,
                        pageData.getDetectedLanguage(),
                        description,
                        pageData.getHttpCode(),
                        pageData.getScreenshot(),
                        pageData.getHomepageText(),
                        pageData.getHttpHeaders()
                );
            } else {
                // No HTTP code (timeout, unknown host, etc.) -> Unreachable (should be removed from list)
                return new DomainResultDTO(
                        domain,
                        "Unreachable",
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
            System.err.println("[DomainCheck] Error analyzing " + domain + ": " + e.getMessage());
            // Treat exceptions as "Unreachable"
            return new DomainResultDTO(
                    domain,
                    "Unreachable",
                    "",
                    "",
                    "Error: " + e.getMessage(),
                    0,
                    null,
                    "",
                    null
            );
        }
    }
}
