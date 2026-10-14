package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.security.OutboundPolicy;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests du service de capture Playwright : filtrage des requêtes du navigateur et capture réelle
 * (ignorée si Chromium n'est pas installé : ./gradlew :backend:installPlaywrightBrowsers).
 */
class PlaywrightScreenshotServiceTest {

    private PlaywrightScreenshotService service;
    private WireMockServer pageServer;
    private WireMockServer forbiddenServer;

    @AfterEach
    void tearDown() {
        if (service != null) service.shutdown();
        if (pageServer != null) pageServer.stop();
        if (forbiddenServer != null) forbiddenServer.stop();
    }

    private static boolean chromiumInstalled() {
        String customPath = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        Path browsers = customPath != null ? Path.of(customPath) : Path.of(System.getProperty("user.home"), ".cache", "ms-playwright");
        try (var entries = Files.list(browsers)) {
            return entries.anyMatch(p -> p.getFileName().toString().startsWith("chromium"));
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Les requêtes du navigateur vers des destinations interdites sont bloquées")
    void testRequestFiltering() {
        service = new PlaywrightScreenshotService(new OutboundPolicy(false, Set.of(80, 443)), true, false, 5000, 1);

        assertFalse(service.isRequestAllowed("http://127.0.0.1/"));
        assertFalse(service.isRequestAllowed("http://169.254.169.254/latest/meta-data/"));
        assertFalse(service.isRequestAllowed("http://localhost/"));
        assertFalse(service.isRequestAllowed("http://8.8.8.8:8080/"));
        assertFalse(service.isRequestAllowed("file:///etc/passwd"));
        assertFalse(service.isRequestAllowed("not a url"));
        assertTrue(service.isRequestAllowed("data:image/png;base64,iVBORw0KGgo="));
        assertTrue(service.isRequestAllowed("https://8.8.8.8/"));
    }

    @Test
    @DisplayName("Une URL interdite n'est jamais ouverte dans le navigateur")
    void testBlockedUrlReturnsNull() throws Exception {
        service = new PlaywrightScreenshotService(new OutboundPolicy(false, Set.of(80, 443)), true, false, 5000, 1);

        assertNull(service.captureScreenshot(URI.create("http://127.0.0.1/").toURL()));
    }

    @Test
    @DisplayName("Capture désactivée par configuration")
    void testDisabled() throws Exception {
        service = new PlaywrightScreenshotService(OutboundPolicy.permissive(), false, false, 5000, 1);

        assertNull(service.captureScreenshot(URI.create("https://example.com/").toURL()));
    }

    @Test
    @DisplayName("Capture réelle en PNG, sous-ressources interdites bloquées")
    void testRealCaptureBlocksForbiddenSubresources() throws Exception {
        assumeTrue(chromiumInstalled(), "Chromium non installé");

        forbiddenServer = new WireMockServer(options().dynamicPort());
        forbiddenServer.start();
        forbiddenServer.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)));

        pageServer = new WireMockServer(options().dynamicPort());
        pageServer.start();
        pageServer.stubFor(get(urlEqualTo("/page")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withBody("<html><head><title>Capture</title></head><body><h1>Hello</h1>"
                        + "<img src=\"http://127.0.0.1:" + forbiddenServer.port() + "/tracker.png\"></body></html>")));

        // Adresses locales autorisées pour le test, mais seul le port du serveur de page est permis
        OutboundPolicy policy = new OutboundPolicy(true, Set.of(pageServer.port()));
        service = new PlaywrightScreenshotService(policy, true, false, 15000, 1);

        byte[] png = service.captureScreenshot(URI.create("http://127.0.0.1:" + pageServer.port() + "/page").toURL());

        assertNotNull(png, "La capture doit réussir");
        assertTrue(png.length > 1000);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);
        assertTrue(forbiddenServer.getAllServeEvents().isEmpty(), "La sous-ressource interdite ne doit pas être chargée");
    }
}
