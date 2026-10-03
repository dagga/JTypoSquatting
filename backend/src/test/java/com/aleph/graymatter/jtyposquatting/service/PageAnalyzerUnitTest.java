package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@com.github.tomakehurst.wiremock.junit5.WireMockTest
class PageAnalyzerUnitTest {

    private PageAnalyzer pageAnalyzer;

    @BeforeEach
    void setUp() {
        // Mock ScreenshotService to avoid JavaFX initialization
        ScreenshotService mockScreenshotService = url -> new byte[0];
        pageAnalyzer = new PageAnalyzer(mockScreenshotService);
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
}
