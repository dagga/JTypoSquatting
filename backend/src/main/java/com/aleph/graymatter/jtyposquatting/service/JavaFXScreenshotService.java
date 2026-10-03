package com.aleph.graymatter.jtyposquatting.service;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

@Service
public class JavaFXScreenshotService implements ScreenshotService {
    private static final Logger logger = LoggerFactory.getLogger(JavaFXScreenshotService.class);
    private static volatile boolean javafxInitialized = false;
    private static final Object javafxLock = new Object();

    @Override
    public byte[] captureScreenshot(URL url) {
        if (url == null) return createPlaceholderScreenshot("Invalid URL");

        AtomicReference<byte[]> screenshotData = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                initJavaFXIfNeeded();
                WebView webView = new WebView();
                webView.getEngine().load(url.toExternalForm());
                webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        try {
                            WritableImage image = webView.snapshot(null, null);
                            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            ImageIO.write(bufferedImage, "png", out);
                            screenshotData.set(out.toByteArray());
                            latch.countDown();
                        } catch (Exception e) {
                            logger.error("Error capturing screenshot", e);
                            latch.countDown();
                        }
                    } else if (newState == Worker.State.FAILED) {
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                logger.error("Error setting up WebView", e);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                logger.error("Screenshot capture timeout for {}", url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return screenshotData.get() != null ? screenshotData.get() : createPlaceholderScreenshot("Screenshot failed");
    }

    private static void initJavaFXIfNeeded() {
        if (javafxInitialized) return;

        synchronized (javafxLock) {
            if (javafxInitialized) return;

            try {
                // ... same initialization as in PageAnalyzer ...
                CountDownLatch initLatch = new CountDownLatch(1);
                Platform.startup(() -> {
                    javafxInitialized = true;
                    initLatch.countDown();
                });
                initLatch.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("JavaFX initialization failed", e);
            }
        }
    }

    private byte[] createPlaceholderScreenshot(String message) {
        // ... same implementation as in PageAnalyzer ...
        return new byte[0]; // Simplified for now
    }
}
