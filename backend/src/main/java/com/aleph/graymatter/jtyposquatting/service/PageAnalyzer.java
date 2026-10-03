package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

    private final ScreenshotService screenshotService;

    @org.springframework.beans.factory.annotation.Autowired
    public PageAnalyzer(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
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
            URL url = new URL(domain.startsWith("http") ? domain : "https://" + domain);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            if (Thread.currentThread().isInterrupted()) {
                logger.debug("Analysis cancelled for {} (interrupted after connection)", domain);
                connection.disconnect();
                data.setHttpCode(0);
                return data;
            }

            if (connection instanceof javax.net.ssl.HttpsURLConnection httpsConn) {
                httpsConn.setHostnameVerifier((hostname, session) -> true);
                try {
                    javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                    sc.init(null, new javax.net.ssl.TrustManager[]{
                            new javax.net.ssl.X509TrustManager() {
                                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                    return null;
                                }

                                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                                }

                                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                                }
                            }
                    }, new java.security.SecureRandom());
                    httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                } catch (Exception ignored) {
                }
            }

            int responseCode = connection.getResponseCode();
            data.setHttpCode(responseCode);

            Map<String, java.util.List<String>> headerFields = connection.getHeaderFields();
            if (headerFields != null) {
                for (Map.Entry<String, java.util.List<String>> entry : headerFields.entrySet()) {
                    String key = entry.getKey();
                    java.util.List<String> values = entry.getValue();
                    if (key != null && values != null && !values.isEmpty()) {
                        data.addHttpHeader(key, String.join("; ", values));
                    }
                }
            }
            
            if (responseCode >= 200 && responseCode < 400) {
                String html = readHtml(connection);
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
            connection.disconnect();
        } catch (java.net.UnknownHostException e) {
            data.setHttpCode(0);
        } catch (java.net.SocketTimeoutException e) {
            data.setHttpCode(0);
        } catch (Exception e) {
            logger.error("Error analyzing page {}: {}", domain, e.getMessage());
        }

        return data;
    }

    private static String readHtml(HttpURLConnection connection) throws Exception {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(16384);
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 3000) {
                sb.append(line).append('\n');
                linesRead++;
            }
            return sb.toString();
        }
    }
}
