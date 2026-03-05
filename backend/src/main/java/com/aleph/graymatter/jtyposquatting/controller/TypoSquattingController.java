package com.aleph.graymatter.jtyposquatting.controller;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.JTypoSquatting;
import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import com.aleph.graymatter.jtyposquatting.service.DomainCheckService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for typo squatting domain generation and checking.
 * Uses Virtual Threads (Java 21+) for efficient parallel domain checking.
 */
@RestController
@RequestMapping("/api")
public class TypoSquattingController {

    private final DomainCheckService domainCheckService;
    private final ConcurrentHashMap<String, ExecutorService> activeSessions = new ConcurrentHashMap<>();
    
    // Virtual thread executor for parallel domain checks
    // Each task runs in its own virtual thread, allowing hundreds of concurrent checks
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public TypoSquattingController(DomainCheckService domainCheckService) {
        this.domainCheckService = domainCheckService;
    }

    @GetMapping(value = "/generate-and-check", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateAndCheckDomains(@RequestParam String domain) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // Create a dedicated virtual thread executor for this SSE session
        // Virtual threads allow hundreds of concurrent domain checks without thread pool tuning
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
                // HTTP code -1 indicates "in progress" (not 0 which means dead/unreachable)
                for (String generatedDomain : generatedDomains) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    DomainResultDTO initialResult = new DomainResultDTO(generatedDomain, "Testing...", "", "", "", -1, null, "", Collections.emptyMap());
                    sendSseEvent(emitter, initialResult);
                    Thread.sleep(2);
                }

                // 3. Check domains in parallel using virtual threads
                // Each domain check runs in its own virtual thread (I/O bound operation)
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
                                System.err.println("Error sending SSE event: " + e.getMessage());
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
                    futures.add(future);
                }

                // Wait for all checks to complete
                for (java.util.concurrent.Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        System.err.println("Error waiting for domain check: " + e.getMessage());
                    }
                }

                // Complete the emitter after all checks are done
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
                e.printStackTrace();
            }
        });

        return emitter;
    }

    @GetMapping("/cancel")
    public ResponseEntity<Void> cancelActiveAnalysis() {
        // Shutdown all active session executors
        for (ExecutorService executor : activeSessions.values()) {
            executor.shutdownNow();
        }
        activeSessions.clear();
        return ResponseEntity.ok().build();
    }

    private void sendSseEvent(SseEmitter emitter, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name("domainUpdate")
                .data(data, MediaType.APPLICATION_JSON)
                .id(String.valueOf(System.currentTimeMillis())));
    }

    private void sendErrorEvent(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Collections.singletonMap("error", message)));
        } catch (IOException e) {
            // Ignore
        }
    }
}
