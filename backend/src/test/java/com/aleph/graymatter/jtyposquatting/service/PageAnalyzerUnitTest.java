package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;
import com.aleph.graymatter.jtyposquatting.security.OutboundPolicy;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@com.github.tomakehurst.wiremock.junit5.WireMockTest(httpsEnabled = true)
class PageAnalyzerUnitTest {

    private PageAnalyzer pageAnalyzer;

    @BeforeEach
    void setUp() {
        // Mock ScreenshotService to avoid JavaFX initialization
        ScreenshotService mockScreenshotService = url -> new byte[0];
        pageAnalyzer = new PageAnalyzer(mockScreenshotService, OutboundPolicy.permissive());
    }

    @Test
    @DisplayName("PageAnalyzer should analyze valid domain and collect data using WireMock")
    void testAnalyzePage_ValidDomain(WireMockRuntimeInfo wmRuntimeInfo) {
        String serverUrl = wmRuntimeInfo.getHttpBaseUrl();
        stubFor(get(urlEqualTo("/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><title>Mocked Aleph Title</title></head><body><h1>Mocked Aleph Content</h1></body></html>")));

        DomainPageDTO result = pageAnalyzer.analyzePage(serverUrl + "/test");

        assertNotNull(result);
        assertEquals(200, result.getHttpCode());
    }

    @Test
    @DisplayName("PageAnalyzer should extract meta description using WireMock")
    void testAnalyzePage_ExtractsMetaDescription(WireMockRuntimeInfo wmRuntimeInfo) {
        String serverUrl = wmRuntimeInfo.getHttpBaseUrl();
        stubFor(get(urlEqualTo("/meta"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><head><meta name=\"description\" content=\"Mocked Description\"></head><body></body></html>")));

        DomainPageDTO result = pageAnalyzer.analyzePage(serverUrl + "/meta");

        assertEquals("Mocked Description", result.getMetaDescription());
    }

    @Test
    @DisplayName("PageAnalyzer should handle invalid domain gracefully")
    void testAnalyzePage_InvalidDomain() {
        String invalidDomain = "http://localhost:1";
        DomainPageDTO result = pageAnalyzer.analyzePage(invalidDomain);

        assertNotNull(result);
        assertEquals(invalidDomain, result.getDomain());
        assertEquals(0, result.getHttpCode());
        assertNull(result.getScreenshot());
    }

    @Test
    @DisplayName("Anti-SSRF : une adresse interne n'est jamais contactée avec la politique stricte")
    void testAnalyzePage_BlocksPrivateAddress(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlEqualTo("/internal")).willReturn(aResponse().withStatus(200).withBody("<html>secret</html>")));
        PageAnalyzer strict = new PageAnalyzer(url -> new byte[0], new OutboundPolicy(false, java.util.Set.of()));

        DomainPageDTO result = strict.analyzePage("http://127.0.0.1:" + wmRuntimeInfo.getHttpPort() + "/internal");

        assertEquals(0, result.getHttpCode());
        verify(0, getRequestedFor(urlEqualTo("/internal")));
    }

    @Test
    @DisplayName("Anti-SSRF : un port non autorisé est refusé")
    void testAnalyzePage_BlocksPort(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlEqualTo("/port")).willReturn(aResponse().withStatus(200)));
        PageAnalyzer strictPorts = new PageAnalyzer(url -> new byte[0], new OutboundPolicy(true, java.util.Set.of(80, 443)));

        DomainPageDTO result = strictPorts.analyzePage(wmRuntimeInfo.getHttpBaseUrl() + "/port");

        assertEquals(0, result.getHttpCode());
        verify(0, getRequestedFor(urlEqualTo("/port")));
    }

    @Test
    @DisplayName("Les redirections sont suivies et revalidées à chaque saut")
    void testAnalyzePage_FollowsAndChecksRedirects(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlEqualTo("/old")).willReturn(aResponse().withStatus(301).withHeader("Location", "/new")));
        stubFor(get(urlEqualTo("/new")).willReturn(aResponse().withStatus(200).withBody("<html><title>New</title></html>")));
        stubFor(get(urlEqualTo("/evil")).willReturn(aResponse().withStatus(302).withHeader("Location", "file:///etc/passwd")));

        DomainPageDTO followed = pageAnalyzer.analyzePage(wmRuntimeInfo.getHttpBaseUrl() + "/old");
        assertEquals(200, followed.getHttpCode());
        assertEquals("New", followed.getTitle());

        DomainPageDTO blocked = pageAnalyzer.analyzePage(wmRuntimeInfo.getHttpBaseUrl() + "/evil");
        assertEquals(0, blocked.getHttpCode());
    }

    @Test
    @DisplayName("Une boucle de redirections est interrompue")
    void testAnalyzePage_RedirectLoop(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlEqualTo("/loop")).willReturn(aResponse().withStatus(302).withHeader("Location", "/loop")));

        DomainPageDTO result = pageAnalyzer.analyzePage(wmRuntimeInfo.getHttpBaseUrl() + "/loop");

        assertEquals(0, result.getHttpCode());
        verify(6, getRequestedFor(urlEqualTo("/loop")));
    }

    @Test
    @DisplayName("TLS : un certificat auto-signé est signalé UNTRUSTED au lieu d'être accepté")
    void testAnalyzePage_SelfSignedCertificate(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlEqualTo("/tls")).willReturn(aResponse().withStatus(200).withBody("<html>tls</html>")));

        DomainPageDTO result = pageAnalyzer.analyzePage(wmRuntimeInfo.getHttpsBaseUrl() + "/tls");

        assertEquals(TlsStatus.UNTRUSTED, result.getTlsStatus());
        verify(0, getRequestedFor(urlEqualTo("/tls")));
    }

    @Test
    @DisplayName("Le corps de réponse est tronqué à 2 Mo")
    void testAnalyzePage_BodySizeLimit(WireMockRuntimeInfo wmRuntimeInfo) {
        String big = "<html><body>" + "a".repeat(3 * 1024 * 1024) + "</body></html>";
        stubFor(get(urlEqualTo("/big")).willReturn(aResponse().withStatus(200).withBody(big)));

        DomainPageDTO result = pageAnalyzer.analyzePage(wmRuntimeInfo.getHttpBaseUrl() + "/big");

        assertEquals(200, result.getHttpCode());
        assertTrue(result.getHtmlContent().length() <= 2 * 1024 * 1024);
    }
}
