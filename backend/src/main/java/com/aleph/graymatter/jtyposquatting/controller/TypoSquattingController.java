package com.aleph.graymatter.jtyposquatting.controller;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.JTypoSquatting;
import com.aleph.graymatter.jtyposquatting.db.DatabaseService;
import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import com.aleph.graymatter.jtyposquatting.service.DomainCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for typo squatting domain generation and checking.
 * Uses Virtual Threads (Java 21+) for efficient parallel domain checking.
 */
@RestController
@RequestMapping("/api")
public class TypoSquattingController {

    private static final Logger logger = LoggerFactory.getLogger(TypoSquattingController.class);

    private final DomainCheckService domainCheckService;
    private final DatabaseService databaseService;
    private final ConcurrentHashMap<String, ExecutorService> activeSessions = new ConcurrentHashMap<>();

    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public TypoSquattingController(DomainCheckService domainCheckService, DatabaseService databaseService) {
        this.domainCheckService = domainCheckService;
        this.databaseService = databaseService;
    }

    @GetMapping(value = "/generate-and-check", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateAndCheckDomains(@RequestParam String domain) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        ExecutorService sessionExecutor = Executors.newVirtualThreadPerTaskExecutor();
        String sessionId = String.valueOf(System.identityHashCode(emitter));
        activeSessions.put(sessionId, sessionExecutor);

        emitter.onCompletion(() -> {
            sessionExecutor.shutdownNow();
            activeSessions.remove(sessionId);
        });
        emitter.onTimeout(() -> {
            sessionExecutor.shutdownNow();
            activeSessions.remove(sessionId);
        });
        emitter.onError(e -> {
            sessionExecutor.shutdownNow();
            activeSessions.remove(sessionId);
        });

        sessionExecutor.submit(() -> {
            try {
                // 1. Generate domains
                JTypoSquatting jTypoSquatting = new JTypoSquatting(domain);
                ArrayList<String> generatedDomains = jTypoSquatting.getListOfDomains();

                // 2. Stream generated domains with "Testing..." status first
                for (String generatedDomain : generatedDomains) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    DomainResultDTO initialResult = new DomainResultDTO(generatedDomain, "Testing...", "", "", "", -1, null, "", Collections.emptyMap());
                    sendSseEvent(emitter, initialResult);
                    Thread.sleep(2);
                }

                // 3. Check domains in parallel
                java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
                for (String generatedDomain : generatedDomains) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    java.util.concurrent.Future<?> future = sessionExecutor.submit(() -> {
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        DomainResultDTO finalResult = domainCheckService.checkDomain(generatedDomain);
                        if (finalResult != null) {
                            try {
                                sendSseEvent(emitter, finalResult);
                                Thread.sleep(2);
                            } catch (IOException | InterruptedException e) {
                                logger.error("Error sending SSE event: {}", e.getMessage());
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
                    futures.add(future);
                }

                for (java.util.concurrent.Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        logger.error("Error waiting for domain check: {}", e.getMessage());
                    }
                }

                emitter.complete();
            } catch (FileNotFoundException e) {
                sendErrorEvent(emitter, "Server error: Required files not found.");
            } catch (InvalidDomainException e) {
                sendErrorEvent(emitter, "Invalid domain name provided.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendErrorEvent(emitter, "Server interrupted.");
            } catch (Exception e) {
                sendErrorEvent(emitter, "Internal server error: " + e.getMessage());
                logger.error("Internal server error", e);
            }
        });

        return emitter;
    }

    @GetMapping("/cancel")
    public ResponseEntity<Void> cancelActiveAnalysis() {
        logger.info("Cancelling {} active session(s)...", activeSessions.size());

        for (Map.Entry<String, ExecutorService> entry : activeSessions.entrySet()) {
            ExecutorService executor = entry.getValue();
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    logger.warn("Session {} did not terminate gracefully", entry.getKey());
                } else {
                    logger.info("Session {} terminated", entry.getKey());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        activeSessions.clear();

        logger.info("All sessions cancelled");
        return ResponseEntity.ok().build();
    }

    /**
     * Cancel all active analysis and clear database.
     * Called by frontend when user clicks "Clear" button.
     */
    @DeleteMapping("/cancel-and-clear")
    public ResponseEntity<Void> cancelAndClear() {
        logger.info("Cancel and clear requested");

        cancelActiveAnalysis();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (databaseService != null) {
            try {
                databaseService.deleteAll();
                logger.info("Database cleared");
            } catch (Exception e) {
                logger.error("Error clearing database: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok().build();
    }

    private void sendSseEvent(SseEmitter emitter, Object data) throws IOException {
        if (data instanceof DomainResultDTO) {
            DomainResultDTO dto = (DomainResultDTO) data;
            byte[] screenshot = dto.getScreenshot();
            logger.debug("Sending SSE event for {} with screenshot: {} bytes", dto.getDomain(), screenshot != null ? screenshot.length : 0);
        }
        emitter.send(SseEmitter.event()
                .name("domainUpdate")
                .data(data, MediaType.APPLICATION_JSON)
                .id(String.valueOf(System.currentTimeMillis())));
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Collections.singletonMap("error", message)));
        } catch (IOException e) {
            // no-op
        }
    }
}
