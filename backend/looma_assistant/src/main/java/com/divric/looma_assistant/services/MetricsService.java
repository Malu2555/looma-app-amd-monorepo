package com.divric.looma_assistant.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * MetricsService — in-memory, thread-safe dashboard telemetry aggregator.
 * <p>
 * Receives completed actor executions and maintains flat, lock-free counters
 * using {@link AtomicLong} / {@link LongAdder} so that high-frequency worker
 * callbacks never block the actor threads. The {@code snapshot()} method
 * produces a flat {@link Map} layout that the REST layer serializes directly
 * into JSON for the Vue frontend telemetry widgets.
 * <p>
 * Tracks: total system requests, per-worker latency (rolling), total token
 * counts, success/failure tallies, and uptime.
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    // ── Counters (non-blocking, thread-safe) ──────────────────────────

    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder totalTokens = new LongAdder();
    private final LongAdder totalLatencyMs = new LongAdder();

    // Per-worker latency accumulators (workerName -> adder)
    private final Map<String, LongAdder> workerLatency = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, LongAdder> workerRequests = new java.util.concurrent.ConcurrentHashMap<>();

    private final AtomicLong lastRequestEpochMs = new AtomicLong(0);
    private final long startupEpochMs = Instant.now().toEpochMilli();

    // ── Recording API (called from actor completion paths) ────────────

    /**
     * Record a completed task execution.
     *
     * @param workerName   the specialised worker that handled the task
     * @param elapsedMs    wall-clock time spent processing
     * @param estimatedTokens estimated token consumption of the task
     * @param success      whether the task completed without failure
     */
    public void recordExecution(String workerName, long elapsedMs, int estimatedTokens, boolean success) {
        totalRequests.increment();
        totalTokens.add(estimatedTokens);
        totalLatencyMs.add(elapsedMs);
        lastRequestEpochMs.set(Instant.now().toEpochMilli());

        workerLatency
                .computeIfAbsent(workerName, k -> new LongAdder())
                .add(elapsedMs);
        workerRequests
                .computeIfAbsent(workerName, k -> new LongAdder())
                .increment();

        if (success) {
            successfulRequests.increment();
        } else {
            failedRequests.increment();
        }

        log.debug("Metrics recorded: worker={}, elapsed={}ms, tokens={}, success={}",
                workerName, elapsedMs, estimatedTokens, success);
    }

    // ── Snapshot (flat map for frontend widgets) ──────────────────────

    /**
     * Produce a flat, serializable snapshot of all current metrics.
     * The structure is intentionally flat (no nested objects) so the
     * Vue telemetry widgets can bind directly to keys.
     *
     * @return immutable flat map of metric name -> value
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> flat = new LinkedHashMap<>();

        long requests = totalRequests.sum();
        long latencySum = totalLatencyMs.sum();

        flat.put("total_requests", requests);
        flat.put("successful_requests", successfulRequests.sum());
        flat.put("failed_requests", failedRequests.sum());
        flat.put("total_tokens", totalTokens.sum());
        flat.put("total_latency_ms", latencySum);
        flat.put("avg_latency_ms", requests > 0 ? (double) latencySum / requests : 0.0);
        flat.put("success_rate", requests > 0
                ? (double) successfulRequests.sum() / requests : 0.0);
        flat.put("last_request_epoch_ms", lastRequestEpochMs.get());
        flat.put("uptime_ms", Instant.now().toEpochMilli() - startupEpochMs);

        // Per-worker breakdown as a flat list of maps
        Map<String, Object> workers = new LinkedHashMap<>();
        workerRequests.forEach((name, adder) -> {
            long wReq = adder.sum();
            long wLat = workerLatency.getOrDefault(name, new LongAdder()).sum();
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("requests", wReq);
            w.put("total_latency_ms", wLat);
            w.put("avg_latency_ms", wReq > 0 ? (double) wLat / wReq : 0.0);
            workers.put(name, w);
        });
        flat.put("workers", workers);

        return flat;
    }

    /**
     * Reset all counters (useful for tests or admin endpoints).
     */
    public void reset() {
        totalRequests.reset();
        successfulRequests.reset();
        failedRequests.reset();
        totalTokens.reset();
        totalLatencyMs.reset();
        workerLatency.clear();
        workerRequests.clear();
        lastRequestEpochMs.set(0);
        log.info("MetricsService counters reset");
    }
}