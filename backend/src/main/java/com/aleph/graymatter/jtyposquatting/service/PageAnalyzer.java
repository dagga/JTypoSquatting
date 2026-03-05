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
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Thread.sleep;

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

    private static volatile boolean javafxInitialized = false;
    private static final Object javafxLock = new Object();

    private static void initJavaFXIfNeeded() {
        if (javafxInitialized) return;

        synchronized (javafxLock) {
            if (javafxInitialized) return;

            try {
                String display = System.getenv("DISPLAY");
                boolean hasDisplay = display != null && !display.isEmpty();
                String existingPrismOrder = System.getProperty("prism.order");
                logger.debug("Initializing JavaFX (DISPLAY={}, prism.order={})", display, existingPrismOrder);

                System.setProperty("prism.vsync", "false");
                System.setProperty("prism.forceGPU", "false");

                if (hasDisplay) {
                    System.setProperty("glass.platform", "gtk");
                    logger.debug("Using GTK glass platform");
                } else {
                    System.setProperty("glass.platform", "monocle");
                    System.setProperty("java.awt.headless", "false");
                    logger.debug("Using monocle glass platform (no DISPLAY)");
                }

                CountDownLatch initLatch = new CountDownLatch(1);
                Platform.startup(() -> {
                    javafxInitialized = true;
                    initLatch.countDown();
                    logger.debug("JavaFX initialized successfully");
                });

                if (!initLatch.await(10, TimeUnit.SECONDS)) {
                    throw new RuntimeException("JavaFX initialization timeout");
                }

            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("Toolkit already initialized")) {
                    javafxInitialized = true;
                    logger.debug("JavaFX already initialized");
                } else {
                    logger.error("JavaFX initialization failed: {}", e.getMessage());
                    throw new RuntimeException("Failed to initialize JavaFX: " + e.getMessage(), e);
                }
            } catch (Exception e) {
                logger.error("JavaFX initialization error: {}", e.getMessage());
                throw new RuntimeException("Failed to initialize JavaFX: " + e.getMessage(), e);
            }
        }
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
            URL url = new URL("https://" + domain);
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
                    // Ignore SSL initialization errors
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
                        // If language detection fails, try with a longer text sample
                        String longerText = textContent.length() > 1000 ? textContent.substring(0, 1000) : textContent;
                        detectedLang = LANGUAGE_DETECTOR.detectLanguageOf(longerText);
                        if (detectedLang != Language.UNKNOWN) {
                            data.setDetectedLanguage(detectedLang.name());
                        }
                    }
                }

                if (Thread.currentThread().isInterrupted()) {
                    logger.debug("Analysis cancelled for {} (interrupted before screenshot)", domain);
                    connection.disconnect();
                    data.setHttpCode(0);
                    return data;
                }

                if (responseCode >= 200 && responseCode < 300 && html != null && !html.trim().isEmpty()) {
                    int textLength = textContent != null ? textContent.length() : 0;
                    logger.debug("Checking screenshot conditions for {} (HTTP={}, textLength={})", domain, responseCode, textLength);

                    if (textLength >= 5) {
                        byte[] screenshot = captureScreenshot(url);
                        data.setScreenshot(screenshot);
                        logger.debug("Screenshot captured for {}: {} bytes", domain, screenshot != null ? screenshot.length : 0);
                    } else {
                        data.setScreenshot(null);
                        logger.debug("No screenshot (text too short: {} chars) for {}", textLength, domain);
                    }
                } else {
                    data.setScreenshot(null);
                    logger.debug("No screenshot (HTTP {}) for {}", responseCode, domain);
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

    /**
     * Capture a screenshot of the page using JavaFX WebView.
     * Renders the page and captures the visible portion as an image.
     */
    private static byte[] captureScreenshot(URL url) {
        logger.debug("captureScreenshot() called for {}", url);

        try {
            initJavaFXIfNeeded();

            if (!javafxInitialized) {
                logger.error("JavaFX not initialized after initJavaFXIfNeeded, returning placeholder");
                return createPlaceholderScreenshot("JavaFX initialization failed");
            }

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<byte[]> result = new AtomicReference<>();
            AtomicReference<Exception> captureException = new AtomicReference<>();
            AtomicReference<String> failureReason = new AtomicReference<>();

            logger.debug("Starting screenshot capture for {}", url);

            Platform.runLater(() -> {
                logger.debug("Platform.runLater() executed for {}", url);
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

                    // Show and focus before loading
                    stage.show();
                    stage.toFront();
                    stage.requestFocus();

                    logger.debug("Stage shown, waiting for pulse");

                    // Wait for JavaFX pulse (first rendering)
                    CountDownLatch pulseLatch = new CountDownLatch(1);
                    Platform.runLater(() -> {
                        try {
                            sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        pulseLatch.countDown();
                    });
                    pulseLatch.await(2, TimeUnit.SECONDS);

                    logger.debug("Loading URL: {}", url);

                    // Load the URL
                    webView.getEngine().load(url.toString());

                    webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                        if (newState == Worker.State.SUCCEEDED) {
                            logger.debug("Page loaded successfully, waiting for JS");

                            // Wait for JavaScript and dynamic content
                            try {
                                for (int i = 0; i < 8; i++) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        logger.debug("Screenshot capture interrupted for {}", url);
                                        latch.countDown();
                                        Platform.runLater(stage::close);
                                        return;
                                    }
                                    sleep(1000);
                                }
                            } catch (InterruptedException e) {
                                logger.debug("Screenshot wait interrupted for {}", url);
                                Thread.currentThread().interrupt();
                                latch.countDown();
                                Platform.runLater(stage::close);
                                return;
                            }

                            // Check for interruption after waiting
                            if (Thread.currentThread().isInterrupted()) {
                                logger.debug("Screenshot capture interrupted after wait for {}", url);
                                latch.countDown();
                                Platform.runLater(stage::close);
                                return;
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

                            // Wait for another JavaFX pulse after resize
                            CountDownLatch resizeLatch = new CountDownLatch(1);
                            Platform.runLater(() -> {
                                try {
                                    sleep(500);
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

                            logger.debug("Capturing snapshot: {}x{}", contentWidth, contentHeight);

                            // Capture snapshot
                            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
                            params.setFill(javafx.scene.paint.Color.WHITE);
                            WritableImage fxImage = new WritableImage(contentWidth, contentHeight);
                            webView.snapshot(params, fxImage);

                            logger.debug("Snapshot captured: {}x{}", contentWidth, contentHeight);

                            // Check pixel data to detect empty/white screenshots
                            javafx.scene.image.PixelReader pixelReader = fxImage.getPixelReader();
                            boolean hasContent = false;
                            if (pixelReader != null) {
                                for (int y = 0; y < contentHeight && !hasContent; y += Math.max(1, contentHeight / 20)) {
                                    for (int x = 0; x < contentWidth && !hasContent; x += Math.max(1, contentWidth / 20)) {
                                        javafx.scene.paint.Color pixelColor = pixelReader.getColor(x, y);
                                        // Check if pixel is not white (with some tolerance)
                                        if (pixelColor.getRed() < 0.99 || pixelColor.getGreen() < 0.99 || pixelColor.getBlue() < 0.99) {
                                            hasContent = true;
                                        }
                                    }
                                }
                                logger.debug("Screenshot has visual content: {}", hasContent);
                            } else {
                                logger.error("PixelReader is null");
                            }

                            if (!hasContent) {
                                logger.debug("Detected empty/white screenshot for {} - returning placeholder", url);
                                // Instead of returning null, create a placeholder with domain info
                                byte[] placeholder = createPlaceholderScreenshot("Page rendered empty");
                                result.set(placeholder);
                                latch.countDown();
                                Platform.runLater(stage::close);
                                return;
                            }

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
                            } else {
                                logger.error("SwingFXUtils.fromFXImage returned null");
                            }

                            g2d.dispose();

                            try {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(thumbnail, "png", baos);
                                byte[] screenshotData = baos.toByteArray();
                                if (screenshotData.length > 0) {
                                    result.set(screenshotData);
                                } else {
                                    failureReason.set("Screenshot data is empty");
                                }
                            } catch (Exception e) {
                                logger.error("Error writing screenshot: {}", e.getMessage());
                                failureReason.set("Error writing screenshot: " + e.getMessage());
                            }
                            latch.countDown();
                            Platform.runLater(stage::close);
                        } else if (newState == Worker.State.FAILED) {
                            logger.error("Page load failed for screenshot: {}", url);
                            failureReason.set("Page load failed");
                            latch.countDown();
                            Platform.runLater(stage::close);
                        }
                    });
                } catch (Exception e) {
                    captureException.set(e);
                    logger.error("Error in captureScreenshot: {}", e.getMessage());
                    latch.countDown();
                }
            });

            try {
                // Wait with timeout, but check for interruption periodically
                for (int i = 0; i < 30; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        logger.debug("Screenshot wait interrupted (latch) for {}", url);
                        return null;
                    }
                    if (latch.await(1, TimeUnit.SECONDS)) {
                        break;
                    }
                }

                if (result.get() != null) {
                    return result.get();
                } else {
                    Exception ex = captureException.get();
                    if (ex != null) {
                        logger.error("Screenshot capture completed but result is null. Exception: {}", ex.getMessage());
                    } else {
                        String reason = failureReason.get();
                        if (reason != null) {
                            logger.error("Screenshot capture failed: {}", reason);
                        } else {
                            logger.error("Screenshot capture completed but result is null (no exception)");
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error waiting for screenshot capture: {}", e.getMessage());
            }
            return null;
        } catch (Exception e) {
            logger.error("Fatal error in captureScreenshot: {}", e.getMessage());
            // Return placeholder instead of null
            return createPlaceholderScreenshot("Error: " + e.getMessage());
        }
    }

    /**
     * Create a placeholder screenshot with error message.
     */
    private static byte[] createPlaceholderScreenshot(String message) {
        try {
            BufferedImage placeholder = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = placeholder.createGraphics();
            
            // White background
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, 320, 240);
            
            // Red border for error
            g2d.setColor(java.awt.Color.RED);
            g2d.setStroke(new java.awt.BasicStroke(3));
            g2d.drawRect(5, 5, 310, 230);
            
            // Error text
            g2d.setColor(java.awt.Color.RED);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
            g2d.drawString("Screenshot unavailable", 70, 100);
            
            g2d.setColor(java.awt.Color.GRAY);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 10));
            g2d.drawString(message.length() > 40 ? message.substring(0, 40) + "..." : message, 20, 130);
            
            g2d.dispose();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(placeholder, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Error creating placeholder: {}", e.getMessage());
            return null;
        }
    }
}
