package com.aleph.graymatter.jtyposquatting.controller;

import com.aleph.graymatter.jtyposquatting.db.DatabaseService;
import com.aleph.graymatter.jtyposquatting.dto.DomainPageDTO;
import com.aleph.graymatter.jtyposquatting.service.DomainCheckService;
import com.aleph.graymatter.jtyposquatting.service.PageAnalyzer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * REST controller for domain data management operations.
 * Provides endpoints for analyzing domains, retrieving cached data, and statistics.
 * Uses Virtual Threads (Java 21+) for efficient I/O operations.
 */
@RestController
@RequestMapping("/api/data")
public class DomainDataController {

    private final DomainCheckService domainCheckService;
    private final DatabaseService databaseService;
    private final PageAnalyzer pageAnalyzer;

    // Map to track active analysis sessions for streaming
    private final Map<String, SseEmitter> activeSessions = new ConcurrentHashMap<>();
    
    // Virtual thread executor for analysis tasks (I/O bound operations)
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    
    @Autowired
    public DomainDataController(DomainCheckService domainCheckService,
                              DatabaseService databaseService,
                              PageAnalyzer pageAnalyzer) {
        this.domainCheckService = domainCheckService;
        this.databaseService = databaseService;
        this.pageAnalyzer = pageAnalyzer;
    }
    
    /**
     * Analyze a single domain and save to database.
     * 
     * @param domain Domain name to analyze
     * @return Analyzed domain data
     */
    @GetMapping("/analyze")
    public ResponseEntity<?> analyzeDomain(@RequestParam String domain) {
        try {
            DomainPageDTO result = pageAnalyzer.analyzePage(domain);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error analyzing domain: " + e.getMessage());
        }
    }
    
    /**
     * Analyze a domain and save to database with streaming results.
     * 
     * @param domain Domain name to analyze
     * @return Server-Sent Events emitter
     */
    @GetMapping(value = "/analyze-and-save", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeAndSave(@RequestParam String domain) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        String sessionId = String.valueOf(System.identityHashCode(emitter));
        activeSessions.put(sessionId, emitter);
        
        // Cleanup on completion/timeout/error
        emitter.onCompletion(() -> activeSessions.remove(sessionId));
        emitter.onTimeout(() -> activeSessions.remove(sessionId));
        emitter.onError(e -> activeSessions.remove(sessionId));
        
        executorService.submit(() -> {
            try {
                DomainPageDTO result = pageAnalyzer.analyzePage(domain);
                databaseService.save(result);
                emitter.send(SseEmitter.event().name("domainUpdate").data(result));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("Analysis failed: " + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    // Ignore
                }
            }
        });
        
        return emitter;
    }
    
    /**
     * Get cached domain data from the database.
     * 
     * @param domain Domain name to retrieve
     * @return Cached domain data
     */
    @GetMapping("/cached/{domain}")
    public ResponseEntity<?> getCachedDomain(@PathVariable String domain) {
        try {
            return databaseService.findByDomain(domain)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error retrieving cached data: " + e.getMessage());
        }
    }
    
    /**
     * Get all cached domains from the database.
     * 
     * @return List of cached domains
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllCachedDomains() {
        try {
            List<DomainPageDTO> domains = databaseService.findAll();
            return ResponseEntity.ok(domains);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error retrieving domains: " + e.getMessage());
        }
    }
    
    /**
     * Clear all cached domain data from the database.
     * 
     * @return Success message
     */
    @DeleteMapping("/all")
    public ResponseEntity<?> clearAllCachedData() {
        try {
            databaseService.deleteAll();
            return ResponseEntity.ok("All cached data cleared");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error clearing data: " + e.getMessage());
        }
    }
    
    /**
     * Get domain statistics.
     * 
     * @return Statistics about the analyzed domains
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStatistics() {
        try {
            int totalDomains = databaseService.count();
            return ResponseEntity.ok(Map.of("totalDomains", totalDomains));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error retrieving statistics: " + e.getMessage());
        }
    }
}
