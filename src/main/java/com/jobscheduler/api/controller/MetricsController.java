package com.jobscheduler.api.controller;

import com.jobscheduler.api.dto.MetricsDto;
import com.jobscheduler.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/metrics")
@Tag(name = "Metrics", description = "System and queue metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/global")
    @Operation(summary = "Get global system metrics")
    public ResponseEntity<MetricsDto> getGlobalMetrics() {
        return ResponseEntity.ok(metricsService.getGlobalMetrics());
    }

    @GetMapping("/queues/{queueId}")
    @Operation(summary = "Get per-queue metrics")
    public ResponseEntity<MetricsDto> getQueueMetrics(@PathVariable UUID queueId) {
        return ResponseEntity.ok(metricsService.getQueueMetrics(queueId));
    }
}
