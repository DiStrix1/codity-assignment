package com.jobscheduler.api.controller;

import com.jobscheduler.api.dto.JobDto;
import com.jobscheduler.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Job management")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @Operation(summary = "Create a job (immediate, delayed, scheduled, recurring)")
    public ResponseEntity<JobDto.Response> createJob(@Valid @RequestBody JobDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createJob(request));
    }

    @PostMapping("/batch")
    @Operation(summary = "Create a batch of jobs sharing a batch_id")
    public ResponseEntity<List<JobDto.Response>> createBatch(@Valid @RequestBody JobDto.BatchCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.createBatch(request));
    }

    @GetMapping
    @Operation(summary = "List jobs with optional filters (status, queueId)")
    public ResponseEntity<Page<JobDto.Response>> listJobs(
            @RequestParam(required = false) UUID queueId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(jobService.listJobs(queueId, status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get job detail with execution history and logs")
    public ResponseEntity<JobDto.DetailResponse> getJobDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJobDetail(id));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a failed or dead-letter job")
    public ResponseEntity<JobDto.Response> retryJob(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.retryJob(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a queued or scheduled job")
    public ResponseEntity<JobDto.Response> cancelJob(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.cancelJob(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a job and its execution history")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        jobService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }
}
