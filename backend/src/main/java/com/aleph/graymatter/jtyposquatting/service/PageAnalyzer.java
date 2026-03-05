package com.aleph.graymatter.jtyposquatting.service;

import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

@Service
public class PageAnalyzer {
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

    private static volatile boolean javafxInitialized = false;
    private static final Object javafxLock = new Object();

    private static void initJavaFXIfNeeded() {
        if (javafxInitialized) return;

        synchronized (javafxLock) {
            if (javafxInitialized) return;

            try {
                // Force software rendering pipeline BEFORE initializing Platform
                System.setProperty("prism.order", "sw");
                System.setProperty("prism.vsync", "false");
                System.setProperty("prism.forceGPU", "false");
                
                Platform.startup(() -> {
                    javafxInitialized = true;
                });
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("Toolkit already initialized")) {
                    javafxInitialized = true;
                } else {
                    throw e;
                }
            }
        }
    }

    public DomainPageDTO analyzePage(String domain) {
        DomainPageDTO data = new DomainPageDTO(domain);

        try {
            URL url = new URL("https://" + domain);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000); // 3 seconds connection timeout
            connection.setReadTimeout(10000); // 10 seconds read timeout
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

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
            
            // Capture HTTP headers
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
                
                // Extract homepage text (first 200 chars)
                if (textContent != null) {
                    data.setHomepageText(textContent.length() > 200 ? textContent.substring(0, 200) + "..." : textContent);
                }
                
                // Extract page title
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
                        // If language detection fails, try with a longer text sample
                        String longerText = textContent.length() > 1000 ? textContent.substring(0, 1000) : textContent;
                        detectedLang = LANGUAGE_DETECTOR.detectLanguageOf(longerText);
                        if (detectedLang != Language.UNKNOWN) {
                            data.setDetectedLanguage(detectedLang.name());
                        }
                    }
                }
                
                // Only take screenshot if HTTP code is exactly 200
                if (responseCode == 200) {
                    data.setScreenshot(captureScreenshot(url));
                } else {
                    data.setScreenshot(null);
                }
            }
            connection.disconnect();
        } catch (java.net.UnknownHostException e) {
            // Domain not found - this is normal for many typo domains
            data.setHttpCode(0);
        } catch (java.net.SocketTimeoutException e) {
            // Connection timeout - normal for dead domains
            data.setHttpCode(0);
        } catch (Exception e) {
            System.err.println("Error analyzing page " + domain + ": " + e.getMessage());
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

    /**
     * Capture a screenshot of the page using JavaFX WebView.
     * Renders the page and captures the visible portion as an image.
     */
    private static byte[] captureScreenshot(URL url) {
        initJavaFXIfNeeded();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> result = new AtomicReference<>();
        
        Platform.runLater(() -> {
            try {
                // Create stage with DECORATED style for proper rendering
                Stage stage = new Stage(StageStyle.DECORATED);
                stage.setWidth(1280);
                stage.setHeight(800);
                stage.setResizable(false);
                stage.setX(100);
                stage.setY(100);

                WebView webView = new WebView();
                webView.setPrefSize(1280, 800);
                webView.getEngine().setJavaScriptEnabled(true);
                
                // Set a user agent string for better compatibility
                webView.getEngine().setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

                VBox root = new VBox();
                root.getChildren().add(webView);

                Scene scene = new Scene(root, 1280, 800);
                scene.setFill(javafx.scene.paint.Color.WHITE);
                stage.setScene(scene);

                // CRITICAL: Show and focus before loading
                stage.show();
                stage.toFront();
                stage.requestFocus();
                
                // Wait for JavaFX pulse (first rendering) - CRITICAL for software rendering
                CountDownLatch pulseLatch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    pulseLatch.countDown();
                });
                pulseLatch.await(2, TimeUnit.SECONDS);

                // Load the URL
                webView.getEngine().load(url.toString());

                webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        // Wait for JavaScript and dynamic content
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        int contentWidth = 1280;
                        int contentHeight = 800;

                        // Get actual content dimensions
                        try {
                            org.w3c.dom.Document doc = webView.getEngine().getDocument();
                            if (doc != null) {
                                org.w3c.dom.Element body = doc.getDocumentElement();
                                if (body != null) {
                                    String width = body.getAttribute("scrollWidth");
                                    String height = body.getAttribute("scrollHeight");
                                    if (width != null && !width.isEmpty()) {
                                        contentWidth = Math.max(800, Math.min(Integer.parseInt(width), 1280));
                                    }
                                    if (height != null && !height.isEmpty()) {
                                        contentHeight = Math.max(600, Math.min(Integer.parseInt(height), 800));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Ignore dimension errors
                        }
                        
                        // Resize WebView to fit content
                        webView.setPrefSize(contentWidth, contentHeight);
                        
                        // Force layout update
                        scene.getRoot().requestLayout();
                        
                        // Wait for another JavaFX pulse after resize - CRITICAL
                        CountDownLatch resizeLatch = new CountDownLatch(1);
                        Platform.runLater(() -> {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            resizeLatch.countDown();
                        });
                        try {
                            resizeLatch.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        // Capture snapshot
                        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
                        params.setFill(javafx.scene.paint.Color.WHITE);
                        WritableImage fxImage = new WritableImage(contentWidth, contentHeight);
                        webView.snapshot(params, fxImage);

                        // Create thumbnail
                        BufferedImage thumbnail = new BufferedImage(
                            320, 240, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g2d = thumbnail.createGraphics();
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                            java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                        g2d.setColor(java.awt.Color.WHITE);
                        g2d.fillRect(0, 0, 320, 240);

                        BufferedImage capturedImage = SwingFXUtils.fromFXImage(fxImage, null);
                        if (capturedImage != null) {
                            g2d.drawImage(capturedImage, 0, 0, 320, 240, null);
                        }

                        g2d.dispose();

                        try {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(thumbnail, "png", baos);
                            byte[] screenshotData = baos.toByteArray();
                            result.set(screenshotData);
                        } catch (Exception e) {
                            // Ignore screenshot writing errors
                        }
                        latch.countDown();
                        Platform.runLater(stage::close);
                    } else if (newState == Worker.State.FAILED) {
                        latch.countDown();
                        Platform.runLater(stage::close);
                    }
                });
            } catch (Exception e) {
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            if (completed && result.get() != null) {
                return result.get();
            }
        } catch (Exception e) {
            // Ignore screenshot capture errors
        }
        return null;
    }
}
