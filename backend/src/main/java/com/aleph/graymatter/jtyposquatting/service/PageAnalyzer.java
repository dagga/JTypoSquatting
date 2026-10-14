package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import com.aleph.graymatter.jtyposquatting.dto.TlsStatus;
import com.aleph.graymatter.jtyposquatting.security.BlockedDestinationException;
import com.aleph.graymatter.jtyposquatting.security.GuardedDnsResolver;
import com.aleph.graymatter.jtyposquatting.security.OutboundPolicy;
import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.IDN;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PageAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(PageAnalyzer.class);
    private static final LanguageDetector LANGUAGE_DETECTOR = LanguageDetectorBuilder
            .fromLanguages(
                    Language.ENGLISH,
                    Language.FRENCH,
                    Language.GERMAN,
                    Language.SPANISH,
                    Language.ITALIAN,
                    Language.PORTUGUESE,
                    Language.DUTCH,
                    Language.RUSSIAN,
                    Language.CHINESE,
                    Language.JAPANESE,
                    Language.KOREAN,
                    Language.ARABIC
            )
            .build();

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    private final ScreenshotService screenshotService;
    private final OutboundPolicy outboundPolicy;
    private final CloseableHttpClient httpClient;

    @org.springframework.beans.factory.annotation.Autowired
    public PageAnalyzer(ScreenshotService screenshotService, OutboundPolicy outboundPolicy) {
        this.screenshotService = screenshotService;
        this.outboundPolicy = outboundPolicy;
        this.httpClient = buildHttpClient(outboundPolicy);
    }

    private static CloseableHttpClient buildHttpClient(OutboundPolicy outboundPolicy) {
        // Default TLS strategy: certificate chain and hostname are verified
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new GuardedDnsResolver(outboundPolicy))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(3))
                        .setSocketTimeout(Timeout.ofSeconds(10))
                        .build())
                .setMaxConnTotal(200)
                .setMaxConnPerRoute(4)
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .setUserAgent(outboundPolicy.getUserAgent())
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build())
                .build();
    }

    @PreDestroy
    public void close() throws IOException {
        httpClient.close();
    }

    public DomainPageDTO analyzePage(String domain) {
        if (Thread.currentThread().isInterrupted()) {
            logger.debug("Analysis cancelled for {} (interrupted)", domain);
            DomainPageDTO data = new DomainPageDTO(domain);
            data.setHttpCode(0);
            return data;
        }

        DomainPageDTO data = new DomainPageDTO(domain);

        try {
            // Internationalised (homoglyph) domains are resolved in their ASCII/punycode form
            URI target = URI.create(domain.startsWith("http") ? domain : "https://" + IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED));
            FetchedPage page = fetchWithFallback(target, data);

            if (Thread.currentThread().isInterrupted()) {
                logger.debug("Analysis cancelled for {} (interrupted after connection)", domain);
                data.setHttpCode(0);
                return data;
            }

            int responseCode = page.statusCode();
            data.setHttpCode(responseCode);
            page.headers().forEach(data::addHttpHeader);
            URL url = page.uri().toURL();

            if (responseCode >= 200 && responseCode < 400) {
                String html = page.body();
                data.setHtmlContent(html);
                
                Document doc = Jsoup.parse(html);
                
                String textContent = doc.text();
                data.setTextContent(textContent);

                if (textContent != null) {
                    data.setHomepageText(textContent.length() > 200 ? textContent.substring(0, 200) + "..." : textContent);
                }

                String title = doc.title();
                data.setTitle(title != null && !title.trim().isEmpty() ? title : "");

                Element el;
                el = doc.selectFirst("meta[name=description]");
                data.setMetaDescription(el != null ? el.attr("content") : null);
                el = doc.selectFirst("meta[name=keywords]");
                data.setMetaKeywords(el != null ? el.attr("content") : null);
                el = doc.selectFirst("meta[name=author]");
                data.setMetaAuthor(el != null ? el.attr("content") : null);
                el = doc.selectFirst("meta[property=og:title]");
                data.setMetaOgTitle(el != null ? el.attr("content") : null);
                el = doc.selectFirst("meta[property=og:description]");
                data.setMetaOgDescription(el != null ? el.attr("content") : null);
                
                if (textContent != null && !textContent.isEmpty()) {
                    Language detectedLang = LANGUAGE_DETECTOR.detectLanguageOf(textContent);
                    if (detectedLang != Language.UNKNOWN) {
                        data.setDetectedLanguage(detectedLang.name());
                    } else {
                        String longerText = textContent.length() > 1000 ? textContent.substring(0, 1000) : textContent;
                        detectedLang = LANGUAGE_DETECTOR.detectLanguageOf(longerText);
                        if (detectedLang != Language.UNKNOWN) {
                            data.setDetectedLanguage(detectedLang.name());
                        }
                    }
                }

                if (responseCode >= 200 && responseCode < 300 && html != null && !html.trim().isEmpty()) {
                    int textLength = textContent != null ? textContent.length() : 0;
                    if (textLength >= 5) {
                        byte[] screenshot = screenshotService.captureScreenshot(url);
                        data.setScreenshot(screenshot);
                    } else {
                        data.setScreenshot(null);
                    }
                } else {
                    data.setScreenshot(null);
                }
            }
        } catch (BlockedDestinationException e) {
            logger.warn("Blocked outbound connection for {}: {}", domain, e.getMessage());
            data.setHttpCode(0);
        } catch (java.net.UnknownHostException | java.io.InterruptedIOException | java.net.SocketException e) {
            // Unknown host, connection refused, no route, connect or read timeout: the domain is unreachable
            logger.debug("{} unreachable: {}", domain, e.getMessage());
            data.setHttpCode(0);
        } catch (Exception e) {
            logger.error("Error analyzing page {}: {}", domain, e.getMessage());
        }

        return data;
    }

    /**
     * Fetches over HTTPS with strict certificate validation. A certificate error is recorded as a
     * {@link TlsStatus} signal and the page is then fetched over plain HTTP instead.
     */
    private FetchedPage fetchWithFallback(URI target, DomainPageDTO data) throws IOException {
        boolean https = "https".equalsIgnoreCase(target.getScheme());
        try {
            FetchedPage page = follow(target);
            if (https) {
                data.setTlsStatus(TlsStatus.VALID);
            }
            return page;
        } catch (SSLException e) {
            if (!https) {
                throw e;
            }
            data.setTlsStatus(classifyTlsFailure(e));
            logger.debug("TLS validation failed for {} ({}), falling back to HTTP", target.getHost(), data.getTlsStatus());
        } catch (ConnectException e) {
            if (!https) {
                throw e;
            }
            logger.debug("HTTPS connection refused for {}, falling back to HTTP", target.getHost());
        }
        return follow(toHttp(target));
    }

    private static URI toHttp(URI uri) {
        try {
            return new URI("http", uri.getUserInfo(), uri.getHost(), -1, uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private FetchedPage follow(URI start) throws IOException {
        URI current = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            // Scheme and port are checked on every hop; hosts are checked by the guarded DNS resolver
            outboundPolicy.checkUrl(current);
            URI requested = current;
            FetchedPage page = httpClient.execute(new HttpGet(requested), response -> readResponse(requested, response));
            if (page.statusCode() < 300 || page.statusCode() >= 400 || page.location() == null) {
                return page;
            }
            current = requested.resolve(page.location().trim());
        }
        throw new IOException("Too many redirects from " + start);
    }

    private static FetchedPage readResponse(URI uri, ClassicHttpResponse response) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : response.getHeaders()) {
            headers.merge(header.getName(), header.getValue(), (a, b) -> a + "; " + b);
        }
        String body = "";
        HttpEntity entity = response.getEntity();
        if (entity != null && response.getCode() >= 200 && response.getCode() < 300) {
            Charset charset = Optional.ofNullable(ContentType.parseLenient(entity.getContentType()))
                    .map(ContentType::getCharset)
                    .orElse(StandardCharsets.UTF_8);
            try (InputStream in = entity.getContent()) {
                body = new String(in.readNBytes(MAX_BODY_BYTES), charset);
            }
        }
        EntityUtils.consumeQuietly(entity);
        Header location = response.getFirstHeader("Location");
        return new FetchedPage(uri, response.getCode(), headers, body, location != null ? location.getValue() : null);
    }

    static TlsStatus classifyTlsFailure(SSLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof CertificateExpiredException || t instanceof CertificateNotYetValidException) {
                return TlsStatus.EXPIRED;
            }
            if (t instanceof SSLPeerUnverifiedException) {
                return TlsStatus.HOSTNAME_MISMATCH;
            }
            if (t instanceof CertPathBuilderException || t instanceof CertPathValidatorException) {
                return TlsStatus.UNTRUSTED;
            }
        }
        String message = String.valueOf(e.getMessage());
        if (message.contains("No subject alternative") || message.contains("doesn't match")) {
            return TlsStatus.HOSTNAME_MISMATCH;
        }
        if (message.contains("certification path") || message.contains("PKIX")) {
            return TlsStatus.UNTRUSTED;
        }
        return TlsStatus.HANDSHAKE_FAILED;
    }

    private record FetchedPage(URI uri, int statusCode, Map<String, String> headers, String body, String location) {
    }
}
