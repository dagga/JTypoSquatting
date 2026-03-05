package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional tests for PageAnalyzer service.
 * Tests real domain analysis including screenshot capture.
 * 
 * These tests verify:
 * 1. Data collection from real domains
 * 2. Screenshot capture functionality
 * 3. Backend data persistence
 */
class PageAnalyzerFunctionalTest {

    private PageAnalyzer pageAnalyzer;

    @BeforeEach
    void setUp() {
        pageAnalyzer = new PageAnalyzer();
    }

    @Test
    @DisplayName("Functional Test: Analyze www.aleph-networks.eu and collect all data")
    void testFunctional_AnalyzeDomain_CollectsAllData() {
        // Given: A valid domain www.aleph-networks.eu

        // When: Analyzing the domain
        DomainPageDTO result = pageAnalyzer.analyzePage("www.aleph-networks.eu");

        // Then: Verify all data is collected
        // 1. Basic information
        assertNotNull(result, "Result should not be null");
        assertEquals("www.aleph-networks.eu", result.getDomain());
        assertEquals(200, result.getHttpCode(), "HTTP code should be 200");

        // 2. HTML content
        assertNotNull(result.getHtmlContent(), "HTML content should not be null");
        assertTrue(result.getHtmlContent().length() > 100, "HTML content should have meaningful size");

        // 3. Text content
        assertNotNull(result.getTextContent(), "Text content should not be null");
        assertFalse(result.getTextContent().trim().isEmpty(), "Text content should not be empty");

        // 4. Homepage text
        assertNotNull(result.getHomepageText(), "Homepage text should not be null");
        assertFalse(result.getHomepageText().trim().isEmpty(), "Homepage text should not be empty");
    }

    @Test
    @DisplayName("Functional Test: Verify screenshot capture for www.aleph-networks.eu")
    void testFunctional_ScreenshotCapture() {
        // Given: A valid domain
        String domain = "www.aleph-networks.eu";

        // When: Analyzing the domain
        DomainPageDTO result = pageAnalyzer.analyzePage(domain);

        // Then: Verify screenshot is captured
        // In CI environment, screenshot is mandatory
        // In local environment without display, screenshot is optional
        boolean isCI = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;
        
        if (isCI) {
            // Strict mode in CI: screenshot is required
            assertNotNull(result.getScreenshot(), "Screenshot should not be null in CI environment");
            assertTrue(result.getScreenshot().length > 500, "Screenshot should have meaningful size in CI");
            System.out.println("CI Mode: Screenshot captured: " + result.getScreenshot().length + " bytes");
        } else {
            // Tolerant mode locally: screenshot is optional
            if (result.getScreenshot() != null) {
                assertTrue(result.getScreenshot().length > 0, "Screenshot should have data");
                System.out.println("Local Mode: Screenshot captured: " + result.getScreenshot().length + " bytes");
            } else {
                System.out.println("Local Mode: Screenshot skipped (no display - install Xvfb to test screenshots)");
            }
        }
    }

    @Test
    @DisplayName("Functional Test: Verify metadata extraction for www.aleph-networks.eu")
    void testFunctional_MetadataExtraction() {
        // Given: A valid domain
        String domain = "www.aleph-networks.eu";

        // When: Analyzing the domain
        DomainPageDTO result = pageAnalyzer.analyzePage(domain);

        // Then: Verify metadata is extracted
        // Title
        assertNotNull(result.getTitle(), "Title should not be null");
        assertFalse(result.getTitle().isEmpty(), "Title should not be empty");
        assertTrue(result.getTitle().contains("Aleph"), "Title should contain 'Aleph'");

        // Meta description
        assertNotNull(result.getMetaDescription(), "Meta description should not be null");
        assertFalse(result.getMetaDescription().isEmpty(), "Meta description should not be empty");

        // Open Graph title
        assertNotNull(result.getMetaOgTitle(), "OG Title should not be null");

        // Language detection
        assertNotNull(result.getDetectedLanguage(), "Language should be detected");
        assertFalse(result.getDetectedLanguage().isEmpty(), "Language should not be empty");
    }

    @Test
    @DisplayName("Functional Test: Complete workflow - analyze and verify backend has data")
    void testFunctional_CompleteWorkflow() {
        // Given: A valid domain
        String domain = "www.aleph-networks.eu";

        // When: Running complete analysis
        DomainPageDTO result = pageAnalyzer.analyzePage(domain);

        // Then: Verify complete workflow
        // 1. HTTP response
        assertEquals(200, result.getHttpCode(), "HTTP code should be 200");

        // 2. Content collected
        assertTrue(result.getHtmlContent().length() > 1000, "Should have HTML content");
        assertTrue(result.getTextContent().length() > 100, "Should have text content");

        // 3. Metadata extracted
        assertFalse(result.getTitle().isEmpty(), "Should have title");
        assertNotNull(result.getMetaDescription(), "Should have meta description");

        // 4. Screenshot captured (strict in CI, optional locally)
        boolean isCI = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;
        if (isCI) {
            assertNotNull(result.getScreenshot(), "Screenshot required in CI");
            assertTrue(result.getScreenshot().length > 500, "Screenshot should have meaningful size in CI");
            System.out.println("CI Mode: Screenshot: " + result.getScreenshot().length + " bytes");
        } else {
            if (result.getScreenshot() != null) {
                assertTrue(result.getScreenshot().length > 0, "Screenshot should have data");
                System.out.println("Local Mode: Screenshot: " + result.getScreenshot().length + " bytes");
            } else {
                System.out.println("Local Mode: Screenshot skipped (no display)");
            }
        }

        // 5. Language detected
        assertNotNull(result.getDetectedLanguage(), "Should detect language");

        System.out.println("=== Functional Test Results ===");
        System.out.println("Domain: " + result.getDomain());
        System.out.println("HTTP Code: " + result.getHttpCode());
        System.out.println("Title: " + result.getTitle());
        System.out.println("Language: " + result.getDetectedLanguage());
        System.out.println("Screenshot: " + (result.getScreenshot() != null ? result.getScreenshot().length + " bytes" : "N/A"));
        System.out.println("HTML Content: " + result.getHtmlContent().length() + " chars");
        System.out.println("Text Content: " + result.getTextContent().length() + " chars");
        System.out.println("===============================");
    }

    @Test
    @DisplayName("Functional Test: Verify backend handles unreachable domains")
    void testFunctional_UnreachableDomain() {
        // Given: An invalid/unreachable domain
        String invalidDomain = "www.this-domain-does-not-exist-12345.com";

        // When: Analyzing the invalid domain
        DomainPageDTO result = pageAnalyzer.analyzePage(invalidDomain);

        // Then: Should handle gracefully
        assertNotNull(result, "Result should not be null");
        assertEquals(0, result.getHttpCode(), "HTTP code should be 0 for unreachable");
        assertNull(result.getScreenshot(), "Screenshot should be null for unreachable");
    }
}
