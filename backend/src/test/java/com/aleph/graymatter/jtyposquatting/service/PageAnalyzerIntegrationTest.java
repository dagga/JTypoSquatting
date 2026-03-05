package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.db.DatabaseService;
import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PageAnalyzer service.
 * Tests real domain analysis including screenshot capture.
 * 
 * Note: These tests require a display for JavaFX screenshot capture.
 * In headless CI environments, use Xvfb or set DISPLAY environment variable.
 */
@SpringBootTest
@ActiveProfiles("test")
class PageAnalyzerIntegrationTest {

    @Autowired
    private PageAnalyzer pageAnalyzer;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private DomainCheckService domainCheckService;

    private static final String TEST_DOMAIN = "www.aleph-networks.eu";

    @BeforeEach
    void setUp() throws Exception {
        // Clear database before each test
        databaseService.deleteAll();
    }

    @Test
    void testPageAnalyzer_CollectsData_ForValidDomain() {
        // Given: A valid domain that responds with HTTP 200

        // When: Analyzing the domain
        DomainPageDTO result = pageAnalyzer.analyzePage(TEST_DOMAIN);

        // Then: Basic data should be collected
        assertNotNull(result, "PageAnalyzer should return non-null result");
        assertEquals(TEST_DOMAIN, result.getDomain(), "Domain name should match");
        assertEquals(200, result.getHttpCode(), "HTTP code should be 200");

        // HTML content should be collected
        assertNotNull(result.getHtmlContent(), "HTML content should not be null");
        assertFalse(result.getHtmlContent().trim().isEmpty(), "HTML content should not be empty");

        // Text content should be extracted
        assertNotNull(result.getTextContent(), "Text content should not be null");
        assertFalse(result.getTextContent().trim().isEmpty(), "Text content should not be empty");

        // Homepage text should be extracted
        assertNotNull(result.getHomepageText(), "Homepage text should not be null");
        assertFalse(result.getHomepageText().trim().isEmpty(), "Homepage text should not be empty");
    }

    @Test
    void testPageAnalyzer_ExtractsMetadata_ForValidDomain() {
        // Given: A valid domain

        // When: Analyzing the domain
        DomainPageDTO result = pageAnalyzer.analyzePage(TEST_DOMAIN);

        // Then: Metadata should be extracted
        assertNotNull(result.getTitle(), "Title should not be null");
        assertFalse(result.getTitle().isEmpty(), "Title should not be empty");
        assertTrue(result.getTitle().contains("Aleph"), "Title should contain 'Aleph'");

        // Meta description should be extracted
        assertNotNull(result.getMetaDescription(), "Meta description should not be null");

        // Open Graph title should be extracted
        assertNotNull(result.getMetaOgTitle(), "OG Title should not be null");

        // Language should be detected
        assertNotNull(result.getDetectedLanguage(), "Detected language should not be null");
        assertFalse(result.getDetectedLanguage().isEmpty(), "Detected language should not be empty");
    }

    @Test
    void testPageAnalyzer_CapturesScreenshot_ForValidDomain() {
        // Given: A valid domain with content

        // When: Analyzing the domain
        DomainPageDTO result = pageAnalyzer.analyzePage(TEST_DOMAIN);

        // Then: Screenshot should be captured
        assertNotNull(result.getScreenshot(), "Screenshot should not be null");
        assertTrue(result.getScreenshot().length > 0, "Screenshot should have data");
        assertTrue(result.getScreenshot().length > 100, "Screenshot should have meaningful size");

        // Log screenshot size for debugging
        System.out.println("Screenshot size: " + result.getScreenshot().length + " bytes");
    }

    @Test
    void testDomainCheckService_ChecksDomain_AndSavesToDatabase() {
        // Given: A valid domain

        // When: Checking the domain through DomainCheckService
        DomainResultDTO result = domainCheckService.checkDomain(TEST_DOMAIN);

        // Then: Result should be valid
        assertNotNull(result, "DomainCheckService should return non-null result");
        assertEquals(TEST_DOMAIN, result.getDomain(), "Domain name should match");

        // Status should be set based on HTTP code
        assertNotNull(result.getStatus(), "Status should not be null");
        assertTrue(
            "Suspicious".equals(result.getStatus()) || "Safe".equals(result.getStatus()),
            "Status should be Suspicious or Safe for HTTP 200"
        );

        // HTTP code should be 200
        assertEquals(200, result.getHttpCode(), "HTTP code should be 200");

        // Screenshot should be captured
        assertNotNull(result.getScreenshot(), "Screenshot should not be null");
        assertTrue(result.getScreenshot().length > 0, "Screenshot should have data");

        // Title should be extracted
        assertNotNull(result.getTitle(), "Title should not be null");
        assertFalse(result.getTitle().isEmpty(), "Title should not be empty");

        // Description should be extracted
        assertNotNull(result.getDescription(), "Description should not be null");
    }

    @Test
    void testDomainCheckService_SavesToDatabase_AfterCheck() throws Exception {
        // Given: A valid domain and empty database
        assertEquals(0, databaseService.count(), "Database should be empty before test");

        // When: Checking the domain
        domainCheckService.checkDomain(TEST_DOMAIN);

        // Then: Data should be saved to database
        int countAfter = databaseService.count();
        assertTrue(countAfter > 0, "Database should have data after check");

        // Verify we can retrieve the data
        var savedData = databaseService.findAll();
        assertNotNull(savedData, "Saved data should not be null");
        assertFalse(savedData.isEmpty(), "Saved data should not be empty");

        // Find our test domain
        var testDomainData = savedData.stream()
            .filter(d -> TEST_DOMAIN.equals(d.getDomain()))
            .findFirst();

        assertTrue(testDomainData.isPresent(), "Test domain should be in database");

        // Verify screenshot is saved
        assertTrue(
            testDomainData.get().getScreenshot() != null && testDomainData.get().getScreenshot().length > 0,
            "Screenshot should be saved in database"
        );
    }

    @Test
    void testPageAnalyzer_HandlesInvalidDomain_Gracefully() {
        // Given: An invalid domain that doesn't exist
        String invalidDomain = "www.this-domain-definitely-does-not-exist-12345.com";

        // When: Analyzing the invalid domain
        DomainPageDTO result = pageAnalyzer.analyzePage(invalidDomain);

        // Then: Should handle gracefully without throwing exception
        assertNotNull(result, "Result should not be null even for invalid domain");
        assertEquals(0, result.getHttpCode(), "HTTP code should be 0 for unreachable domain");
        assertNull(result.getScreenshot(), "Screenshot should be null for unreachable domain");
    }

    @Test
    void testFullAnalysisWorkflow() throws Exception {
        // Given: Empty database
        databaseService.deleteAll();

        // When: Running full analysis workflow
        DomainResultDTO result = domainCheckService.checkDomain(TEST_DOMAIN);

        // Then: Verify complete workflow
        // 1. Data collected
        assertNotNull(result);
        assertEquals(200, result.getHttpCode());
        assertNotNull(result.getScreenshot());
        assertTrue(result.getScreenshot().length > 0);

        // 2. Data saved to database
        int dbCount = databaseService.count();
        assertTrue(dbCount > 0, "Data should be saved to database");

        // 3. Screenshot persisted
        var dbData = databaseService.findAll();
        var persistedScreenshot = dbData.stream()
            .filter(d -> TEST_DOMAIN.equals(d.getDomain()))
            .findFirst()
            .map(DomainPageDTO::getScreenshot)
            .orElse(null);

        assertNotNull(persistedScreenshot, "Screenshot should be persisted in database");
        assertTrue(persistedScreenshot.length > 0, "Persisted screenshot should have data");
    }
}
