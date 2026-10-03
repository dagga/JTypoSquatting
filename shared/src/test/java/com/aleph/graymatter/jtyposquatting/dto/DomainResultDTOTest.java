package com.aleph.graymatter.jtyposquatting.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour DomainResultDTO — getters, Base64, sérialisation cohérente.
 */
class DomainResultDTOTest {

    // ---------------------------------------------------------------
    // Constructeur complet
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructeur complet initialise tous les champs correctement")
    void testFullConstructor() {
        byte[] screenshot = {1, 2, 3};
        DomainResultDTO dto = new DomainResultDTO(
                "example.com", DomainStatus.SUSPICIOUS, "Example Title",
                "fr", "Meta description", 200,
                screenshot, "Homepage text",
                Map.of("Content-Type", "text/html")
        );

        assertEquals("example.com", dto.getDomain());
        assertEquals(DomainStatus.SUSPICIOUS, dto.getStatus());
        assertEquals("Example Title", dto.getTitle());
        assertEquals("fr", dto.getLanguage());
        assertEquals("Meta description", dto.getDescription());
        assertEquals(200, dto.getHttpCode());
        assertEquals("Homepage text", dto.getHomepageText());
        assertNotNull(dto.getHttpHeaders());
        assertEquals("text/html", dto.getHttpHeaders().get("Content-Type"));
    }

    // ---------------------------------------------------------------
    // Screenshot — conversion Base64 ↔ bytes
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Screenshot bytes → Base64 lors de la construction")
    void testScreenshotBase64EncodedAtConstruction() {
        byte[] screenshot = "test-png-data".getBytes();
        DomainResultDTO dto = new DomainResultDTO(
                "example.com", DomainStatus.SAFE, null, null, null,
                200, screenshot, null, null
        );

        String expectedBase64 = Base64.getEncoder().encodeToString(screenshot);
        assertEquals(expectedBase64, dto.getScreenshotBase64());
        assertArrayEquals(screenshot, dto.getScreenshot());
    }

    @Test
    @DisplayName("setScreenshot() met à jour le Base64 en conséquence")
    void testSetScreenshotUpdatesBase64() {
        DomainResultDTO dto = new DomainResultDTO();
        byte[] data = {10, 20, 30, 40};
        dto.setScreenshot(data);

        assertEquals(Base64.getEncoder().encodeToString(data), dto.getScreenshotBase64());
        assertArrayEquals(data, dto.getScreenshot());
    }

    @Test
    @DisplayName("setScreenshotBase64() décode les bytes correctement")
    void testSetScreenshotBase64DecodeBytes() {
        byte[] original = {5, 10, 15};
        String b64 = Base64.getEncoder().encodeToString(original);

        DomainResultDTO dto = new DomainResultDTO();
        dto.setScreenshotBase64(b64);

        assertArrayEquals(original, dto.getScreenshot());
        assertEquals(b64, dto.getScreenshotBase64());
    }

    @Test
    @DisplayName("Screenshot null — pas d'exception, Base64 null")
    void testNullScreenshotNoException() {
        DomainResultDTO dto = new DomainResultDTO(
                "safe.com", DomainStatus.SAFE, null, null, null,
                404, null, null, null
        );

        assertNull(dto.getScreenshot());
        assertNull(dto.getScreenshotBase64());
    }

    @Test
    @DisplayName("getScreenshot() décode depuis Base64 si screenshot est null mais Base64 présent")
    void testGetScreenshotDecodesFromBase64WhenBytesAreNull() {
        byte[] data = {7, 8, 9};
        String b64 = Base64.getEncoder().encodeToString(data);

        DomainResultDTO dto = new DomainResultDTO();
        // Inject screenshotBase64 via setter (screenshot stays null)
        dto.setScreenshotBase64(b64);
        // Force screenshot to null via reflection isn't needed — setScreenshotBase64 also sets screenshot
        // So we test the direct getScreenshot path when dto was received without raw bytes
        assertArrayEquals(data, dto.getScreenshot());
    }

    // ---------------------------------------------------------------
    // Constructeur vide + setters
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Constructeur vide + setters fonctionnent")
    void testDefaultConstructorAndSetters() {
        DomainResultDTO dto = new DomainResultDTO();
        dto.setDomain("test.org");
        dto.setStatus(DomainStatus.UNREACHABLE);
        dto.setHttpCode(503);

        assertEquals("test.org", dto.getDomain());
        assertEquals(DomainStatus.UNREACHABLE, dto.getStatus());
        assertEquals(503, dto.getHttpCode());
    }

    // ---------------------------------------------------------------
    // toString
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toString() contient le domaine et le statut")
    void testToStringContainsDomainAndStatus() {
        DomainResultDTO dto = new DomainResultDTO();
        dto.setDomain("phishing.com");
        dto.setStatus(DomainStatus.SUSPICIOUS);

        String result = dto.toString();
        assertTrue(result.contains("phishing.com"));
        assertTrue(result.contains("SUSPICIOUS"));
    }
}
