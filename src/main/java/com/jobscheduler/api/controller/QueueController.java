package com.jobscheduler.api.controller;

import com.jobscheduler.api.dto.QueueDto;
import com.jobscheduler.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/queues")
@Tag(name = "Queues", description = "Queue management")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping
    @Operation(summary = "Create a new queue")
    public ResponseEntity<QueueDto.Response> createQueue(@Valid @RequestBody QueueDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(queueService.createQueue(request));
    }

    @GetMapping
    @Operation(summary = "List queues with optional project filter")
    public ResponseEntity<Page<QueueDto.Response>> listQueues(
            @RequestParam(required = false) UUID projectId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(queueService.listQueues(projectId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get queue by ID")
    public ResponseEntity<QueueDto.Response> getQueue(@PathVariable UUID id) {
        return ResponseEntity.ok(queueService.getQueue(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update queue")
    public ResponseEntity<QueueDto.Response> updateQueue(@PathVariable UUID id, @Valid @RequestBody QueueDto.UpdateRequest request) {
        return ResponseEntity.ok(queueService.updateQueue(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete queue")
    public ResponseEntity<Void> deleteQueue(@PathVariable UUID id) {
        queueService.deleteQueue(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause a queue — workers will stop claiming from it")
    public ResponseEntity<QueueDto.Response> pauseQueue(@PathVariable UUID id) {
        return ResponseEntity.ok(queueService.pauseQueue(id));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a paused queue")
    public ResponseEntity<QueueDto.Response> resumeQueue(@PathVariable UUID id) {
        return ResponseEntity.ok(queueService.resumeQueue(id));
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "Get queue statistics (pending, running, completed, failed, DLQ counts)")
    public ResponseEntity<QueueDto.StatsResponse> getQueueStats(@PathVariable UUID id) {
        return ResponseEntity.ok(queueService.getQueueStats(id));
    }
}
