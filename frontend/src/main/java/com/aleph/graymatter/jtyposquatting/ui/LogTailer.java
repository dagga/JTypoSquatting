package com.aleph.graymatter.jtyposquatting.ui;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles tailing log files and updating UI components with new lines
 */
public class LogTailer {
    private final Path path;
    private final JTextArea textArea;
    private final JCheckBox followCheck;
    private long lastPos = 0;
    private ScheduledFuture<?> future;
    private final Deque<String> buffer = new ArrayDeque<>();
    private final int maxLines = 1000; // Reduced from 5000 to 1000
    private final Object lock = new Object();
    private volatile boolean isRunning = false;

    public LogTailer(String filePath, JTextArea textArea, JCheckBox followCheck) {
        this.path = Paths.get(filePath);
        this.textArea = textArea;
        this.followCheck = followCheck;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;

        // Initialize by loading last 30 lines
        try {
            if (java.nio.file.Files.exists(path)) {
                java.util.List<String> all = java.nio.file.Files.readAllLines(path);
                int start = Math.max(0, all.size() - 30);
                java.util.List<String> last = all.subList(start, all.size());
                synchronized (lock) {
                    buffer.clear();
                    for (String l : last) buffer.addLast(l);
                }
                final String initial = String.join("\n", last);
                SwingUtilities.invokeLater(() -> {
                    textArea.setText(initial);
                    if (followCheck.isSelected()) textArea.setCaretPosition(textArea.getDocument().getLength());
                });
                lastPos = java.nio.file.Files.size(path);
            }
        } catch (Exception e) {
            // ignore initial read
        }

        try (ScheduledExecutorService logExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LogTailer");
            t.setDaemon(true);
            return t;
        })) {

            // Increased interval from 500ms to 1000ms to reduce CPU usage
            future = logExecutor.scheduleWithFixedDelay(() -> {
                try {
                    if (!java.nio.file.Files.exists(path)) return;
                    long len = java.nio.file.Files.size(path);
                    if (len < lastPos) {
                        // rotated
                        lastPos = 0;
                    }
                    if (len > lastPos) {
                        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(path.toFile(), "r")) {
                            raf.seek(lastPos);
                            String line;
                            java.util.List<String> newLines = new java.util.ArrayList<>();
                            int lineCount = 0;
                            while ((line = raf.readLine()) != null && lineCount < 100) { // Limit to 100 lines per read
                                lineCount++;
                                newLines.add(new String(line.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), java.nio.charset.StandardCharsets.UTF_8));
                            }
                            lastPos = raf.getFilePointer();
                            if (!newLines.isEmpty()) {
                                boolean trimmed = false;
                                synchronized (lock) {
                                    for (String l : newLines) buffer.addLast(l);
                                    while (buffer.size() > maxLines) {
                                        buffer.removeFirst();
                                        trimmed = true;
                                    }
                                }
                                if (trimmed) {
                                    // Build full text in background
                                    String full;
                                    synchronized (lock) {
                                        full = String.join("\n", buffer);
                                    }
                                    final String out = full;
                                    SwingUtilities.invokeLater(() -> {
                                        textArea.setText(out);
                                        if (followCheck.isSelected())
                                            textArea.setCaretPosition(textArea.getDocument().getLength());
                                    });
                                } else {
                                    final String out = String.join("\n", newLines) + "\n";
                                    SwingUtilities.invokeLater(() -> {
                                        textArea.append(out);
                                        if (followCheck.isSelected())
                                            textArea.setCaretPosition(textArea.getDocument().getLength());
                                    });
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        isRunning = false;
        if (future != null) {
            future.cancel(true);
            future = null;
        }
    }

    public void clear() {
        synchronized (lock) {
            buffer.clear();
        }
        lastPos = 0;
    }
}