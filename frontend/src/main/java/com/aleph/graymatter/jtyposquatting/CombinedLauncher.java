package com.aleph.graymatter.jtyposquatting;

import com.aleph.graymatter.jtyposquatting.ui.JTypoFrame;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import com.aleph.graymatter.jtyposquatting.JTypoSquattingApplication;

public class CombinedLauncher {
    private static final Logger logger = LoggerFactory.getLogger(CombinedLauncher.class);

    public static void main(String[] args) {
        // Start Backend in a separate thread
        new Thread(() -> {
            new SpringApplicationBuilder(JTypoSquattingApplication.class)
                    // Desktop mode: API reachable from this machine only, no login
                    .properties("server.port=8081", "server.address=127.0.0.1", "jtypo.security.mode=local")
                    .run(args);
        }).start();
        
        // Wait for Backend to start on 8081
        logger.info("Waiting for backend to start on 8081...");
        boolean backendStarted = false;
        while (!backendStarted) {
            try {
                Thread.sleep(1000);
                try (Socket s = new Socket("127.0.0.1", 8081)) {
                    backendStarted = true;
                    logger.info("Backend is reachable.");
                } catch (Exception e) {
                    logger.debug("Backend not ready yet... (reason: {})", e.getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        // Start Frontend
        logger.info("Launching frontend...");
        JTypoFrame.main(args);
    }
}
