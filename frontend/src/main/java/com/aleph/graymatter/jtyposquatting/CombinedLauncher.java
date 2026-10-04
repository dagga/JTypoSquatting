package com.aleph.graymatter.jtyposquatting;

import com.aleph.graymatter.jtyposquatting.ui.JTypoFrame;
import java.net.Socket;

public class CombinedLauncher {
    public static void main(String[] args) {
        // Start Backend in a separate thread
        new Thread(() -> {
            System.setProperty("server.port", "8081");
            com.aleph.graymatter.jtyposquatting.JTypoSquattingApplication.main(args);
        }).start();
        
        // Wait for Backend to start on 8081
        System.out.println("Waiting for backend to start on 8081...");
        boolean backendStarted = false;
        while (!backendStarted) {
            try {
                Thread.sleep(1000);
                try (Socket s = new Socket("127.0.0.1", 8081)) {
                    backendStarted = true;
                    System.out.println("Backend is reachable.");
                } catch (Exception e) {
                    System.out.println("Backend not ready yet... (reason: " + e.getMessage() + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        // Start Frontend
        System.out.println("Launching frontend...");
        JTypoFrame.main(args);
    }
}
