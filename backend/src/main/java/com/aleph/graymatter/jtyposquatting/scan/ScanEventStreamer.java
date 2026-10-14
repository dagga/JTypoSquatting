package com.aleph.graymatter.jtyposquatting.scan;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Streams the results of a scan as Server-Sent Events.
 * <p>
 * Results are read from the database, so the stream works whichever instance runs the scan, and a
 * client that reconnects with {@code Last-Event-ID} receives only the results it missed.
 * <ul>
 *   <li>{@code result}: one checked domain ({@link ScanResultDTO}), event id = result id</li>
 *   <li>{@code progress}: scan status and counters ({@link ScanDTO}), when they change</li>
 *   <li>{@code end}: final state of the scan, then the stream is closed</li>
 * </ul>
 */
@Component
public class ScanEventStreamer {
    private static final Logger logger = LoggerFactory.getLogger(ScanEventStreamer.class);
    private static final int BATCH_SIZE = 200;

    private final ScanRepository scans;
    private final ScanResultRepository results;
    private final Duration pollInterval;
    private final Duration keepAliveInterval;
    private final Duration maxStreamDuration;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger activeStreams = new AtomicInteger();

    public ScanEventStreamer(ScanRepository scans, ScanResultRepository results, MeterRegistry meters,
                             @Value("${jtypo.sse.poll-interval-ms:500}") long pollIntervalMillis,
                             @Value("${jtypo.sse.keep-alive-ms:15000}") long keepAliveMillis,
                             @Value("${jtypo.sse.max-duration-ms:1800000}") long maxDurationMillis) {
        this.scans = scans;
        this.results = results;
        this.pollInterval = Duration.ofMillis(pollIntervalMillis);
        this.keepAliveInterval = Duration.ofMillis(keepAliveMillis);
        this.maxStreamDuration = Duration.ofMillis(maxDurationMillis);
        meters.gauge("jtypo.sse.streams", activeStreams);
    }

    public SseEmitter stream(String scanId, long lastEventId) {
        // The client reconnects by itself (with Last-Event-ID) when a long stream is closed
        SseEmitter emitter = new SseEmitter(maxStreamDuration.toMillis() + 60_000);
        executor.submit(() -> {
            activeStreams.incrementAndGet();
            try {
                pump(scanId, lastEventId, emitter);
            } finally {
                activeStreams.decrementAndGet();
            }
        });
        return emitter;
    }

    private void pump(String scanId, long lastEventId, SseEmitter emitter) {
        long lastId = lastEventId;
        ScanDTO lastProgress = null;
        Instant deadline = Instant.now().plus(maxStreamDuration);
        Instant nextKeepAlive = Instant.now().plus(keepAliveInterval);
        try {
            while (Instant.now().isBefore(deadline)) {
                // Read the status before the results: a scan seen as finished has all its results stored
                Scan scan = scans.findById(scanId).orElseThrow(() -> new ScanNotFoundException(scanId));
                List<ScanResult> batch;
                do {
                    batch = results.findAfter(scanId, lastId, BATCH_SIZE);
                    for (ScanResult result : batch) {
                        emitter.send(SseEmitter.event()
                                .id(String.valueOf(result.id()))
                                .name("result")
                                .data(ScanResultDTO.of(result), MediaType.APPLICATION_JSON));
                        lastId = result.id();
                    }
                } while (batch.size() == BATCH_SIZE);

                ScanDTO progress = ScanDTO.of(scan);
                if (!scan.status().isActive()) {
                    emitter.send(SseEmitter.event().name("end").data(progress, MediaType.APPLICATION_JSON));
                    emitter.complete();
                    return;
                }
                if (!Objects.equals(progress, lastProgress)) {
                    emitter.send(SseEmitter.event().name("progress").data(progress, MediaType.APPLICATION_JSON));
                    lastProgress = progress;
                    nextKeepAlive = Instant.now().plus(keepAliveInterval);
                } else if (Instant.now().isAfter(nextKeepAlive)) {
                    // Keeps proxies from closing an idle connection
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                    nextKeepAlive = Instant.now().plus(keepAliveInterval);
                }
                Thread.sleep(pollInterval.toMillis());
            }
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            // Client disconnected, or the response could not be written: release the request in any case
            logger.debug("SSE stream of scan {} closed", scanId, e);
            try {
                emitter.completeWithError(e);
            } catch (RuntimeException ignored) {
                // Already completed
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException e) {
            logger.warn("SSE stream of scan {} failed", scanId, e);
            emitter.completeWithError(e);
        }
    }
}
