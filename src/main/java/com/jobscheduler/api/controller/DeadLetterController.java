package com.jobscheduler.api.controller;

import com.jobscheduler.api.dto.DeadLetterDto;
import com.jobscheduler.api.dto.JobDto;
import com.jobscheduler.api.exception.ResourceNotFoundException;
import com.jobscheduler.domain.entity.DeadLetterEntry;
import com.jobscheduler.domain.repository.DeadLetterRepository;
import com.jobscheduler.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dlq")
@Tag(name = "Dead Letter Queue", description = "Dead letter queue management")
public class DeadLetterController {

    private final DeadLetterRepository deadLetterRepository;
    private final JobService jobService;

    public DeadLetterController(DeadLetterRepository deadLetterRepository, JobService jobService) {
        this.deadLetterRepository = deadLetterRepository;
        this.jobService = jobService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List DLQ entries")
    public ResponseEntity<Page<DeadLetterDto.Response>> listDlqEntries(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<DeadLetterEntry> entries = deadLetterRepository.findAllByOrderByCreatedAtDesc(pageable);
        return ResponseEntity.ok(entries.map(this::toResponse));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Re-enqueue a dead letter entry")
    public ResponseEntity<JobDto.Response> retryDlqEntry(@PathVariable UUID id) {
        DeadLetterEntry entry = deadLetterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DLQ Entry", id));
        return ResponseEntity.ok(jobService.retryJob(entry.getOriginalJob().getId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Purge a DLQ entry")
    public ResponseEntity<Void> deleteDlqEntry(@PathVariable UUID id) {
        DeadLetterEntry entry = deadLetterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DLQ Entry", id));
        deadLetterRepository.delete(entry);
        return ResponseEntity.noContent().build();
    }

    private DeadLetterDto.Response toResponse(DeadLetterEntry e) {
        return DeadLetterDto.Response.builder()
                .id(e.getId())
                .originalJobId(e.getOriginalJob().getId())
                .queueId(e.getQueue().getId())
                .queueName(e.getQueue().getName())
                .payload(e.getPayload())
                .totalAttempts(e.getTotalAttempts())
                .finalError(e.getFinalError())
                .finalStackTrace(e.getFinalStackTrace())
                .failedAt(e.getFailedAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
