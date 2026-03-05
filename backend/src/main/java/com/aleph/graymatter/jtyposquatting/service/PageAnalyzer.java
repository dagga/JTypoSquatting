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
                // Check if DISPLAY is available
                String display = System.getenv("DISPLAY");
                boolean hasDisplay = display != null && !display.isEmpty();
                
                // Check existing prism.order property (may be set by JVM args)
                String existingPrismOrder = System.getProperty("prism.order");
                System.out.println("[PageAnalyzer] Initializing JavaFX (DISPLAY=" + display + ", prism.order=" + existingPrismOrder + ")");

                // Don't override prism.order if already set by JVM arguments
                // Just ensure other settings are correct
                System.setProperty("prism.vsync", "false");
                System.setProperty("prism.forceGPU", "false");
                
                if (hasDisplay) {
                    System.setProperty("glass.platform", "gtk");
                    System.out.println("[PageAnalyzer] Using GTK glass platform");
                } else {
                    System.setProperty("glass.platform", "monocle");
                    System.setProperty("java.awt.headless", "false");
                    System.out.println("[PageAnalyzer] Using monocle glass platform (no DISPLAY)");
                }

                // Initialize JavaFX Platform
                CountDownLatch initLatch = new CountDownLatch(1);
                AtomicReference<Exception> initException = new AtomicReference<>();
                
                Platform.startup(() -> {
                    javafxInitialized = true;
                    initLatch.countDown();
                    System.out.println("[PageAnalyzer] JavaFX initialized successfully");
                });

                // Wait for initialization
                if (!initLatch.await(10, TimeUnit.SECONDS)) {
                    Exception ex = initException.get();
                    if (ex != null) {
                        throw new RuntimeException("JavaFX initialization timeout", ex);
                    }
                    throw new RuntimeException("JavaFX initialization timeout after 10 seconds");
                }

            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("Toolkit already initialized")) {
                    javafxInitialized = true;
                    System.out.println("[PageAnalyzer] JavaFX already initialized");
                } else {
                    System.err.println("[PageAnalyzer] JavaFX initialization failed (IllegalStateException): " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Failed to initialize JavaFX: " + e.getMessage(), e);
                }
            } catch (Exception e) {
                System.err.println("[PageAnalyzer] JavaFX initialization error: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Failed to initialize JavaFX: " + e.getMessage(), e);
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
                
                // Only take screenshot if HTTP code is 2xx and we have valid HTML content
                if (responseCode >= 200 && responseCode < 300 && html != null && !html.trim().isEmpty()) {
                    // Additional check: ensure the page has some minimal content
                    if (textContent != null && textContent.length() > 10) {
                        byte[] screenshot = captureScreenshot(url);
                        data.setScreenshot(screenshot);
                        System.out.println("[PageAnalyzer] Screenshot captured for " + domain + ": " + (screenshot != null ? screenshot.length : 0) + " bytes");
                        if (screenshot == null) {
                            System.out.println("[PageAnalyzer] Screenshot capture returned null for " + domain);
                        }
                    } else {
                        data.setScreenshot(null);
                        System.out.println("[PageAnalyzer] No screenshot (text too short: " + (textContent != null ? textContent.length() : 0) + " chars) for " + domain);
                    }
                } else {
                    data.setScreenshot(null);
                    System.out.println("[PageAnalyzer] No screenshot (HTTP " + responseCode + ", HTML empty: " + (html == null || html.trim().isEmpty()) + ") for " + domain);
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
        try {
            initJavaFXIfNeeded();

            // Verify JavaFX is actually initialized
            if (!javafxInitialized) {
                System.err.println("[PageAnalyzer] JavaFX not initialized after initJavaFXIfNeeded, returning placeholder");
                return createPlaceholderScreenshot("JavaFX initialization failed");
            }

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<byte[]> result = new AtomicReference<>();
            AtomicReference<Exception> captureException = new AtomicReference<>();
            AtomicReference<String> failureReason = new AtomicReference<>();

            System.out.println("[PageAnalyzer] Starting screenshot capture for " + url);

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

                    System.out.println("[PageAnalyzer] Stage shown, waiting for pulse");

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

                    System.out.println("[PageAnalyzer] Loading URL: " + url);

                    // Load the URL
                    webView.getEngine().load(url.toString());

                    webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                        if (newState == Worker.State.SUCCEEDED) {
                            System.out.println("[PageAnalyzer] Page loaded successfully, waiting for JS");
                            
                            // Wait for JavaScript and dynamic content (increased to 5 seconds)
                            try {
                                Thread.sleep(5000);
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

                            System.out.println("[PageAnalyzer] Capturing snapshot: " + contentWidth + "x" + contentHeight);

                            // Capture snapshot
                            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
                            params.setFill(javafx.scene.paint.Color.WHITE);
                            WritableImage fxImage = new WritableImage(contentWidth, contentHeight);
                            webView.snapshot(params, fxImage);

                            // Debug: check if image has content
                            System.out.println("[PageAnalyzer] Snapshot captured: " + contentWidth + "x" + contentHeight);

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
                                System.out.println("[PageAnalyzer] Screenshot has visual content: " + hasContent);
                            } else {
                                System.err.println("[PageAnalyzer] PixelReader is null");
                            }

                            if (!hasContent) {
                                System.out.println("[PageAnalyzer] Detected empty/white screenshot for " + url);
                                failureReason.set("Blank screenshot detected");
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
                                System.err.println("[PageAnalyzer] SwingFXUtils.fromFXImage returned null");
                            }

                            g2d.dispose();

                            try {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(thumbnail, "png", baos);
                                byte[] screenshotData = baos.toByteArray();
                                // Check if screenshot is not empty
                                if (screenshotData.length > 0) {
                                    result.set(screenshotData);
                                } else {
                                    failureReason.set("Screenshot data is empty");
                                }
                            } catch (Exception e) {
                                System.err.println("[PageAnalyzer] Error writing screenshot: " + e.getMessage());
                                failureReason.set("Error writing screenshot: " + e.getMessage());
                            }
                            latch.countDown();
                            Platform.runLater(stage::close);
                        } else if (newState == Worker.State.FAILED) {
                            System.err.println("[PageAnalyzer] Page load failed for screenshot: " + url);
                            failureReason.set("Page load failed");
                            latch.countDown();
                            Platform.runLater(stage::close);
                        }
                    });
                } catch (Exception e) {
                    captureException.set(e);
                    System.err.println("[PageAnalyzer] Error in captureScreenshot: " + e.getMessage());
                    e.printStackTrace();
                    latch.countDown();
                }
            });

            try {
                boolean completed = latch.await(30, TimeUnit.SECONDS);
                if (completed && result.get() != null) {
                    return result.get();
                } else if (completed && result.get() == null) {
                    Exception ex = captureException.get();
                    if (ex != null) {
                        System.err.println("[PageAnalyzer] Screenshot capture completed but result is null. Exception: " + ex.getMessage());
                    } else {
                        String reason = failureReason.get();
                        if (reason != null) {
                            System.err.println("[PageAnalyzer] Screenshot capture failed: " + reason);
                        } else {
                            System.err.println("[PageAnalyzer] Screenshot capture completed but result is null (no exception)");
                        }
                    }
                } else {
                    System.err.println("[PageAnalyzer] Screenshot capture timed out after 30 seconds for " + url);
                }
            } catch (Exception e) {
                System.err.println("[PageAnalyzer] Error waiting for screenshot capture: " + e.getMessage());
            }
            return null;
        } catch (Exception e) {
            System.err.println("[PageAnalyzer] Fatal error in captureScreenshot: " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("[PageAnalyzer] Error creating placeholder: " + e.getMessage());
            return null;
        }
    }
}
