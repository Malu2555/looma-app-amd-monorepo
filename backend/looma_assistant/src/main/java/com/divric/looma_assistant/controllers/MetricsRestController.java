package com.divric.looma_assistant.controllers;

import com.divric.looma_assistant.services.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MetricsRestController — exposes engine analytics to the Vue frontend
 * telemetry dashboard.
 * <p>
 * Returns a flat JSON snapshot (latency, token counts, overall system
 * requests) produced by {@link MetricsService}. The flat layout is designed
 * to feed the frontend telemetry widgets directly without nested parsing.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsRestController {

    private final MetricsService metricsService;

    public MetricsRestController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * GET /api/metrics
     * <p>
     * Returns the current flat metrics snapshot.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(metricsService.snapshot());
    }

    /**
     * POST /api/metrics/reset
     * <p>
     * Clears all accumulated counters (admin / debug use).
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetMetrics() {
        metricsService.reset();
        return ResponseEntity.ok(metricsService.snapshot());
    }
}