package com.aleph.graymatter.jtyposquatting.ui;

import com.aleph.graymatter.jtyposquatting.client.JTypoSquattingRestClient;
import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(DomainStreamingService.class);
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

    // Track processing state: true = actively being processed (orange), false = waiting (white)
    private final Map<String, Boolean> processingState = new HashMap<>();

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
                                logger.debug("Received: {} screenshot={} bytes", result.getDomain(), screenshot.length);
                            }

                            if (!domainRowMap.containsKey(result.getDomain())) {
                                domainRowMap.put(result.getDomain(), totalCount.get());
                                totalCount.incrementAndGet();

                                if ("Testing...".equals(result.getStatus())) {
                                    testingDomainTimestamps.put(result.getDomain(), System.currentTimeMillis());
                                    processingState.put(result.getDomain(), false); // Start as waiting (white)
                                    scheduleTimeoutCheck(result.getDomain());
                                }
                            } else {
                                testingDomainTimestamps.remove(result.getDomain());
                                processingState.remove(result.getDomain());
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
                // Mark as processing (orange) - domain is being actively checked
                processingState.put(domain, true);
                
                // Remove from testing timestamps but keep in domainRowMap (don't remove from grid)
                testingDomainTimestamps.remove(domain);

                // Notify UI immediately on the EDT - keep status as Testing... but it's now processing
                SwingUtilities.invokeLater(() -> {
                    DomainResultDTO processingUpdate = new DomainResultDTO(domain, "Testing...", "", "", "", -1, null, "", null);
                    onDomainUpdate.accept(processingUpdate);
                });
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
        processingState.clear();
        activeCount.set(0);
        deadCount.set(0);
        totalCount.set(0);

        logger.debug("Reset complete");
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

    /**
     * Check if a domain is actively being processed (orange status) or waiting (white status).
     * @param domain The domain to check
     * @return true if processing (orange), false if waiting (white)
     */
    public boolean isProcessing(String domain) {
        return processingState.getOrDefault(domain, false);
    }
}