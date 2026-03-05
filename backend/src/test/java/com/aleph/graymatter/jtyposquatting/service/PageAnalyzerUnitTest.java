package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PageAnalyzer service.
 * Tests basic functionality without Spring Boot context.
 */
class PageAnalyzerUnitTest {

    private final PageAnalyzer pageAnalyzer = new PageAnalyzer();

    @Test
    @DisplayName("PageAnalyzer should analyze valid domain and collect data")
    void testAnalyzePage_ValidDomain() {
        // Given
        String domain = "www.aleph-networks.eu";

        // When
        DomainPageDTO result = pageAnalyzer.analyzePage(domain);

        // Then
        assertNotNull(result);
        assertEquals(domain, result.getDomain());
        assertEquals(200, result.getHttpCode());
        assertNotNull(result.getHtmlContent());
        assertFalse(result.getHtmlContent().isEmpty());
    }

    @Test
    @DisplayName("PageAnalyzer should extract title from valid domain")
    void testAnalyzePage_ExtractsTitle() {
        // Given
        String domain = "www.aleph-networks.eu";

        // When
        DomainPageDTO result = pageAnalyzer.analyzePage(domain);

        // Then
        assertNotNull(result.getTitle());
        assertFalse(result.getTitle().isEmpty());
        assertTrue(result.getTitle().contains("Aleph"));
    }

    @Test
    @DisplayName("PageAnalyzer should capture screenshot for valid domain")
    void testAnalyzePage_CapturesScreenshot() {
        // Given
        String domain = "www.aleph-networks.eu";

        // When
        DomainPageDTO result = pageAnalyzer.analyzePage(domain);

        // Then: Screenshot is optional in local environment (requires DISPLAY)
        // In CI with Xvfb, screenshot should be present
        boolean isCI = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;
        
        if (isCI) {
            // Strict mode in CI
            assertNotNull(result.getScreenshot(), "Screenshot required in CI");
            assertTrue(result.getScreenshot().length > 500, "Screenshot should have meaningful size");
            System.out.println("CI Mode: Screenshot captured: " + result.getScreenshot().length + " bytes");
        } else {
            // Tolerant mode locally
            if (result.getScreenshot() != null && result.getScreenshot().length > 0) {
                System.out.println("Local Mode: Screenshot captured: " + result.getScreenshot().length + " bytes");
            } else {
                System.out.println("Local Mode: Screenshot skipped (no DISPLAY - install Xvfb to test)");
            }
        }
    }

    @Test
    @DisplayName("PageAnalyzer should handle invalid domain gracefully")
    void testAnalyzePage_InvalidDomain() {
        // Given
        String invalidDomain = "www.invalid-domain-xyz-12345.com";

        // When
        DomainPageDTO result = pageAnalyzer.analyzePage(invalidDomain);

        // Then
        assertNotNull(result);
        assertEquals(invalidDomain, result.getDomain());
        assertEquals(0, result.getHttpCode());
        assertNull(result.getScreenshot());
    }

    @Test
    @DisplayName("PageAnalyzer should detect language")
    void testAnalyzePage_DetectsLanguage() {
        // Given
        String domain = "www.aleph-networks.eu";

        // When
        DomainPageDTO result = pageAnalyzer.analyzePage(domain);

        // Then
        assertNotNull(result.getDetectedLanguage());
        assertFalse(result.getDetectedLanguage().isEmpty());
    }

    @Test
    @DisplayName("PageAnalyzer should extract meta description")
    void testAnalyzePage_ExtractsMetaDescription() {
        // Given
        String domain = "www.aleph-networks.eu";

        // When
        DomainPageDTO result = pageAnalyzer.analyzePage(domain);

        // Then
        assertNotNull(result.getMetaDescription());
    }

    @Test
    @DisplayName("PageAnalyzer should extract Open Graph title")
    void testAnalyzePage_ExtractsOgTitle() {
        // Given
        String domain = "www.aleph-networks.eu";

        // When
        DomainPageDTO result = pageAnalyzer.analyzePage(domain);

        // Then
        assertNotNull(result.getMetaOgTitle());
    }
}
