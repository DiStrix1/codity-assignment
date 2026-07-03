package com.jobscheduler.api.controller;

import com.jobscheduler.api.dto.WorkerDto;
import com.jobscheduler.api.exception.ResourceNotFoundException;
import com.jobscheduler.domain.entity.Worker;
import com.jobscheduler.domain.enums.WorkerStatus;
import com.jobscheduler.domain.repository.WorkerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workers")
@Tag(name = "Workers", description = "Worker monitoring")
public class WorkerController {

    private final WorkerRepository workerRepository;

    public WorkerController(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    @GetMapping
    @Operation(summary = "List all workers, optionally filtered by status")
    public ResponseEntity<List<WorkerDto.Response>> listWorkers(
            @RequestParam(required = false) String status) {
        List<Worker> workers;
        if (status != null) {
            workers = workerRepository.findByStatus(WorkerStatus.valueOf(status.toUpperCase()));
        } else {
            workers = workerRepository.findAll();
        }
        return ResponseEntity.ok(workers.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get worker details")
    public ResponseEntity<WorkerDto.Response> getWorker(@PathVariable UUID id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", id));
        return ResponseEntity.ok(toResponse(worker));
    }

    @DeleteMapping("/offline")
    @Operation(summary = "Clear all offline workers")
    public ResponseEntity<Void> clearOfflineWorkers() {
        workerRepository.deleteOfflineWorkers();
        return ResponseEntity.noContent().build();
    }

    private WorkerDto.Response toResponse(Worker w) {
        return WorkerDto.Response.builder()
                .id(w.getId())
                .hostname(w.getHostname())
                .status(w.getStatus().name())
                .maxConcurrentJobs(w.getMaxConcurrentJobs())
                .currentJobCount(w.getCurrentJobCount())
                .registeredAt(w.getRegisteredAt())
                .lastHeartbeatAt(w.getLastHeartbeatAt())
                .build();
    }
}
