package com.jobscheduler.service;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.jobscheduler.api.dto.JobDto;
import com.jobscheduler.api.exception.BadRequestException;
import com.jobscheduler.api.exception.ConflictException;
import com.jobscheduler.api.exception.ResourceNotFoundException;
import com.jobscheduler.domain.entity.*;
import com.jobscheduler.domain.entity.Queue;
import com.jobscheduler.domain.enums.JobStatus;
import com.jobscheduler.domain.enums.JobType;
import com.jobscheduler.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;
    private final JobExecutionRepository executionRepository;
    private final JobLogRepository logRepository;
    private final ScheduledJobRepository scheduledJobRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final RetryPolicyRepository retryPolicyRepository;

    private final CronParser cronParser = new CronParser(
            CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    public JobService(JobRepository jobRepository,
                      QueueRepository queueRepository,
                      JobExecutionRepository executionRepository,
                      JobLogRepository logRepository,
                      ScheduledJobRepository scheduledJobRepository,
                      DeadLetterRepository deadLetterRepository,
                      RetryPolicyRepository retryPolicyRepository) {
        this.jobRepository = jobRepository;
        this.queueRepository = queueRepository;
        this.executionRepository = executionRepository;
        this.logRepository = logRepository;
        this.scheduledJobRepository = scheduledJobRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.retryPolicyRepository = retryPolicyRepository;
    }

    @Transactional
    public JobDto.Response createJob(JobDto.CreateRequest request) {
        Queue queue = queueRepository.findById(request.getQueueId())
                .orElseThrow(() -> new ResourceNotFoundException("Queue", request.getQueueId()));

        // Check idempotency
        if (request.getIdempotencyKey() != null && jobRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new ConflictException("Job with idempotency key '" + request.getIdempotencyKey() + "' already exists");
        }

        JobType type = parseJobType(request.getType());

        // For RECURRING type, create a ScheduledJob entry instead
        if (type == JobType.RECURRING) {
            return createRecurringJob(request, queue);
        }

        // Determine max attempts from queue's retry policy
        int maxAttempts = 3;
        if (queue.getRetryPolicy() != null) {
            maxAttempts = queue.getRetryPolicy().getMaxAttempts();
        }

        JobStatus initialStatus = (type == JobType.DELAYED || type == JobType.SCHEDULED)
                ? JobStatus.SCHEDULED : JobStatus.QUEUED;

        Job job = Job.builder()
                .queue(queue)
                .type(type)
                .status(initialStatus)
                .payload(request.getPayload() != null ? request.getPayload() : Map.of())
                .idempotencyKey(request.getIdempotencyKey())
                .batchId(request.getBatchId())
                .priority(request.getPriority())
                .maxAttempts(maxAttempts)
                .scheduledAt(request.getScheduledAt())
                .build();

        job = jobRepository.save(job);
        log.info("Created job {} of type {} in queue {}", job.getId(), type, queue.getName());
        return toResponse(job);
    }

    @Transactional
    public List<JobDto.Response> createBatch(JobDto.BatchCreateRequest request) {
        Queue queue = queueRepository.findById(request.getQueueId())
                .orElseThrow(() -> new ResourceNotFoundException("Queue", request.getQueueId()));

        UUID batchId = UUID.randomUUID();
        int maxAttempts = queue.getRetryPolicy() != null ? queue.getRetryPolicy().getMaxAttempts() : 3;

        List<Job> jobs = request.getJobs().stream()
                .map(item -> {
                    if (item.getIdempotencyKey() != null && jobRepository.existsByIdempotencyKey(item.getIdempotencyKey())) {
                        throw new ConflictException("Duplicate idempotency key: " + item.getIdempotencyKey());
                    }
                    return Job.builder()
                            .queue(queue)
                            .type(JobType.BATCH)
                            .status(JobStatus.QUEUED)
                            .payload(item.getPayload())
                            .idempotencyKey(item.getIdempotencyKey())
                            .batchId(batchId)
                            .priority(request.getPriority())
                            .maxAttempts(maxAttempts)
                            .build();
                })
                .toList();

        jobs = jobRepository.saveAll(jobs);
        log.info("Created batch of {} jobs with batchId {}", jobs.size(), batchId);
        return jobs.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<JobDto.Response> listJobs(UUID queueId, String status, Pageable pageable) {
        Specification<Job> spec = Specification.where(null);

        if (queueId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("queue").get("id"), queueId));
        }
        if (status != null && !status.isBlank()) {
            JobStatus jobStatus = JobStatus.valueOf(status.toUpperCase());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), jobStatus));
        }

        return jobRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public JobDto.DetailResponse getJobDetail(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));

        List<JobExecution> executions = executionRepository.findByJobIdOrderByAttemptNumberAsc(jobId);
        List<JobLog> logs = logRepository.findByJobIdOrderByCreatedAtAsc(jobId, Pageable.ofSize(100)).getContent();

        return JobDto.DetailResponse.builder()
                .job(toResponse(job))
                .executions(executions.stream().map(this::toExecutionResponse).toList())
                .recentLogs(logs.stream().map(this::toLogResponse).toList())
                .build();
    }

    @Transactional
    public JobDto.Response retryJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));

        if (job.getStatus() != JobStatus.FAILED && job.getStatus() != JobStatus.DEAD_LETTER) {
            throw new BadRequestException("Can only retry FAILED or DEAD_LETTER jobs");
        }

        // Remove from DLQ if present
        deadLetterRepository.findByOriginalJobId(jobId)
                .ifPresent(deadLetterRepository::delete);

        // Reset job for retry
        job.setStatus(JobStatus.QUEUED);
        job.setAttemptCount(0);
        job.setClaimedAt(null);
        job.setClaimedByWorker(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);
        job.setErrorMessage(null);
        job = jobRepository.save(job);

        log.info("Retrying job {}", jobId);
        return toResponse(job);
    }

    @Transactional
    public JobDto.Response cancelJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));

        if (job.getStatus() != JobStatus.QUEUED && job.getStatus() != JobStatus.SCHEDULED) {
            throw new BadRequestException("Can only cancel QUEUED or SCHEDULED jobs");
        }

        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage("Cancelled by user");
        job.setCompletedAt(Instant.now());
        job = jobRepository.save(job);

        log.info("Cancelled job {}", jobId);
        return toResponse(job);
    }

    @Transactional
    public void deleteJob(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));
        jobRepository.delete(job);
        log.info("Deleted job {} and associated executions/logs", jobId);
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private JobDto.Response createRecurringJob(JobDto.CreateRequest request, Queue queue) {
        if (request.getCronExpression() == null || request.getCronExpression().isBlank()) {
            throw new BadRequestException("Cron expression required for RECURRING jobs");
        }

        Cron cron = cronParser.parse(request.getCronExpression());
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime nextFire = executionTime.nextExecution(ZonedDateTime.now(ZoneId.of("UTC")))
                .orElseThrow(() -> new BadRequestException("Invalid cron expression: no future execution"));

        ScheduledJob scheduledJob = ScheduledJob.builder()
                .queue(queue)
                .cronExpression(request.getCronExpression())
                .payload(request.getPayload() != null ? request.getPayload() : Map.of())
                .enabled(true)
                .nextFireAt(nextFire.toInstant())
                .build();
        scheduledJobRepository.save(scheduledJob);

        // Also create the first job instance
        Job job = Job.builder()
                .queue(queue)
                .type(JobType.RECURRING)
                .status(JobStatus.SCHEDULED)
                .payload(request.getPayload() != null ? request.getPayload() : Map.of())
                .idempotencyKey(request.getIdempotencyKey())
                .priority(request.getPriority())
                .maxAttempts(queue.getRetryPolicy() != null ? queue.getRetryPolicy().getMaxAttempts() : 3)
                .scheduledAt(nextFire.toInstant())
                .build();
        job = jobRepository.save(job);

        log.info("Created recurring job with cron '{}', next fire at {}", request.getCronExpression(), nextFire);
        return toResponse(job);
    }

    private JobType parseJobType(String type) {
        if (type == null || type.isBlank()) return JobType.IMMEDIATE;
        try {
            return JobType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid job type: " + type);
        }
    }

    private JobDto.Response toResponse(Job job) {
        return JobDto.Response.builder()
                .id(job.getId())
                .queueId(job.getQueue().getId())
                .queueName(job.getQueue().getName())
                .type(job.getType().name())
                .status(job.getStatus().name())
                .payload(job.getPayload())
                .idempotencyKey(job.getIdempotencyKey())
                .batchId(job.getBatchId())
                .priority(job.getPriority())
                .attemptCount(job.getAttemptCount())
                .maxAttempts(job.getMaxAttempts())
                .scheduledAt(job.getScheduledAt())
                .claimedAt(job.getClaimedAt())
                .claimedByWorker(job.getClaimedByWorker())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private JobDto.ExecutionResponse toExecutionResponse(JobExecution exec) {
        return JobDto.ExecutionResponse.builder()
                .id(exec.getId())
                .attemptNumber(exec.getAttemptNumber())
                .workerId(exec.getWorkerId())
                .status(exec.getStatus().name())
                .startedAt(exec.getStartedAt())
                .endedAt(exec.getEndedAt())
                .durationMs(exec.getDurationMs())
                .errorMessage(exec.getErrorMessage())
                .stackTrace(exec.getStackTrace())
                .build();
    }

    private JobDto.LogResponse toLogResponse(JobLog jobLog) {
        return JobDto.LogResponse.builder()
                .id(jobLog.getId())
                .level(jobLog.getLevel().name())
                .message(jobLog.getMessage())
                .metadata(jobLog.getMetadata())
                .createdAt(jobLog.getCreatedAt())
                .build();
    }
}
