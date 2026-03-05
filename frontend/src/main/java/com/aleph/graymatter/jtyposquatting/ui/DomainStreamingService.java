package com.aleph.graymatter.jtyposquatting.ui;

import com.aleph.graymatter.jtyposquatting.client.JTypoSquattingRestClient;
import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service for managing domain streaming and updates
 */
public class DomainStreamingService {
    private final JTypoSquattingRestClient restClient;
    private final Gson gson = new GsonBuilder().create();
    private final ExecutorService executorService;
    private final ScheduledExecutorService timeoutExecutor;
    private AutoCloseable activeStreamHandle;
    private final Map<String, Integer> domainRowMap = new HashMap<>();
    private final Map<String, Long> testingDomainTimestamps = new HashMap<>();
    private static final int DOMAIN_TIMEOUT_MS = 5000; // 5 seconds for testing

    // Statistics
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicInteger deadCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);

    // Callbacks
    private Consumer<DomainResultDTO> onDomainUpdate;
    private Runnable onStreamComplete;
    private Consumer<String> onStreamError;

    public DomainStreamingService(JTypoSquattingRestClient restClient) {
        this.restClient = restClient;
        this.executorService = Executors.newSingleThreadExecutor();
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TimeoutChecker");
            t.setDaemon(true);
            return t;
        });
    }

    public void setCallbacks(Consumer<DomainResultDTO> onDomainUpdate, Runnable onStreamComplete, Consumer<String> onStreamError) {
        this.onDomainUpdate = onDomainUpdate;
        this.onStreamComplete = onStreamComplete;
        this.onStreamError = onStreamError;
    }

    public void startDomainChecks(String domain) {
        // Clear previous state
        domainRowMap.clear();
        testingDomainTimestamps.clear();
        activeCount.set(0);
        deadCount.set(0);
        totalCount.set(0);

        // If there's an existing stream, cancel it first
        cancelActiveStreamIfAny();

        final ConcurrentLinkedQueue<String> eventQueue = new ConcurrentLinkedQueue<>();
        final AtomicBoolean scheduled = new AtomicBoolean(false);
        final int BATCH_SIZE = 50; // Increased from 20 to 50
        final int BATCH_DELAY_MS = 200; // Increased from 100 to 200ms

        final Runnable[] drainRef = new Runnable[1];

        executorService.submit(() -> {
            try {
                // Clear backend database before starting stream
                try {
                    restClient.clearAllCachedData();
                } catch (Exception e) {
                    // Ignore cache clearing errors
                }

                Runnable drain = () -> {
                    try {
                        int batch = 0;
                        String json;

                        while (batch < BATCH_SIZE && (json = eventQueue.poll()) != null) {
                            DomainResultDTO result = gson.fromJson(json, DomainResultDTO.class);
                            byte[] screenshot = result.getScreenshot();
                            if (screenshot != null) {
                                System.out.println("[DomainStreamingService] Received: " + result.getDomain() + " screenshot=" + screenshot.length + " bytes");
                            }

                            if (!domainRowMap.containsKey(result.getDomain())) {
                                domainRowMap.put(result.getDomain(), totalCount.get());
                                totalCount.incrementAndGet();

                                if ("Testing...".equals(result.getStatus())) {
                                    testingDomainTimestamps.put(result.getDomain(), System.currentTimeMillis());
                                    scheduleTimeoutCheck(result.getDomain());
                                }
                            } else {
                                testingDomainTimestamps.remove(result.getDomain());
                                if ("Active".equals(result.getStatus())) activeCount.incrementAndGet();
                                else if ("Dead".equals(result.getStatus())) deadCount.incrementAndGet();
                            }

                            onDomainUpdate.accept(result);
                            batch++;
                        }
                    } finally {
                        scheduled.set(false);
                        if (!eventQueue.isEmpty() && scheduled.compareAndSet(false, true)) {
                            SwingUtilities.invokeLater(drainRef[0]);
                        }
                    }
                };

                drainRef[0] = drain;

                activeStreamHandle = restClient.streamDomainChecks(domain, (resultJson) -> {
                    eventQueue.add(resultJson);
                    if (scheduled.compareAndSet(false, true)) {
                        SwingUtilities.invokeLater(drainRef[0]);
                    }
                }, () -> SwingUtilities.invokeLater(() -> {
                    Timer completionTimer = new Timer(100, null);
                    completionTimer.addActionListener(ev -> {
                        if (eventQueue.isEmpty()) {
                            completionTimer.stop();
                            onStreamComplete.run();
                        }
                    });
                    completionTimer.start();
                }), (error) -> {
                    SwingUtilities.invokeLater(() -> onStreamError.accept(error));
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    onStreamError.accept("Failed to start stream: " + e.getMessage());
                });
            }
        });
    }

    private void scheduleTimeoutCheck(String domain) {
        timeoutExecutor.schedule(() -> {
            // Only process if domain is still in testing state
            if (testingDomainTimestamps.containsKey(domain)) {
                // Remove from tracking first
                testingDomainTimestamps.remove(domain);
                domainRowMap.remove(domain);
                totalCount.decrementAndGet();
                
                // Notify UI immediately on the EDT
                SwingUtilities.invokeLater(() -> onDomainUpdate.accept(new DomainResultDTO(domain, "Timeout", "", "", "", 0, null, "", null)));
            }
        }, DOMAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    public void cancelActiveStreamIfAny() {
        AutoCloseable handle = activeStreamHandle;
        if (handle != null) {
            try {
                handle.close();
            } catch (Exception ignored) {}
            activeStreamHandle = null;
        }
    }

    public void reset() {
        // Cancel any active stream
        cancelActiveStreamIfAny();
        
        // Clear all internal state
        domainRowMap.clear();
        testingDomainTimestamps.clear();
        activeCount.set(0);
        deadCount.set(0);
        totalCount.set(0);
        
        System.out.println("[DomainStreamingService] Reset complete");
    }

    public void shutdown() {
        try {
            cancelActiveStreamIfAny();
            executorService.shutdown();
            timeoutExecutor.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getActiveCount() {
        return activeCount.get();
    }

    public int getDeadCount() {
        return deadCount.get();
    }

    public int getTotalCount() {
        return totalCount.get();
    }
}