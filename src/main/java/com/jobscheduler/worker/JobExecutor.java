package com.jobscheduler.worker;

import com.jobscheduler.domain.entity.*;
import com.jobscheduler.domain.enums.*;
import com.jobscheduler.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jobscheduler.websocket.JobEventPublisher;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Executes a single claimed job:
 * 1. Creates a JobExecution record
 * 2. Transitions job to RUNNING
 * 3. Invokes the JobHandler
 * 4. On success: marks COMPLETED
 * 5. On failure: applies retry policy or moves to DLQ
 */
@Service
public class JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final JobLogRepository logRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final QueueRepository queueRepository;
    private final RetryPolicyEngine retryPolicyEngine;
    private final JobHandler jobHandler;
    private final JobEventPublisher jobEventPublisher;

    public JobExecutor(JobRepository jobRepository,
            JobExecutionRepository executionRepository,
            JobLogRepository logRepository,
            DeadLetterRepository deadLetterRepository,
            QueueRepository queueRepository,
            RetryPolicyEngine retryPolicyEngine,
            SimulatedJobHandler jobHandler,
            JobEventPublisher jobEventPublisher) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.logRepository = logRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.queueRepository = queueRepository;
        this.retryPolicyEngine = retryPolicyEngine;
        this.jobHandler = jobHandler;
        this.jobEventPublisher = jobEventPublisher;
    }

    /**
     * Execute a single job. This method runs on a virtual thread.
     * Uses REQUIRES_NEW to ensure each execution has its own transaction boundary.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(Job job, UUID workerId) {
        Instant startTime = Instant.now();
        String idempotencyKey = job.getId() + ":" + job.getAttemptCount();

        // Create execution record
        JobExecution execution = JobExecution.builder()
                .job(job)
                .attemptNumber(job.getAttemptCount())
                .workerId(workerId)
                .status(ExecutionStatus.RUNNING)
                .startedAt(startTime)
                .idempotencyKey(idempotencyKey)
                .build();
        execution = executionRepository.save(execution);

        // Transition to RUNNING
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(startTime);
        jobRepository.save(job);
        jobEventPublisher.publishJobUpdate(job.getId(), "RUNNING", job.getQueue().getId());

        writeLog(job, execution, LogLevel.INFO, "Job execution started (attempt " + job.getAttemptCount() + ")");

        try {
            // Execute the job
            JobHandler.JobResult result = jobHandler.execute(job);

            // Success
            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

            execution.setStatus(ExecutionStatus.COMPLETED);
            execution.setEndedAt(endTime);
            execution.setDurationMs(durationMs);
            executionRepository.save(execution);

            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(endTime);
            job.setErrorMessage(null);
            jobRepository.save(job);
            jobEventPublisher.publishJobUpdate(job.getId(), "COMPLETED", job.getQueue().getId());

            writeLog(job, execution, LogLevel.INFO,
                    "Job completed in " + durationMs + "ms: " + result.message());

            log.info("Job {} completed in {}ms", job.getId(), durationMs);

        } catch (Exception e) {
            handleFailure(job, execution, workerId, startTime, e);
        }
    }

    private void handleFailure(Job job, JobExecution execution, UUID workerId,
            Instant startTime, Exception error) {
        Instant endTime = Instant.now();
        long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        String stackTrace = getStackTrace(error);

        // Update execution record
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setEndedAt(endTime);
        execution.setDurationMs(durationMs);
        execution.setErrorMessage(error.getMessage());
        execution.setStackTrace(stackTrace);
        executionRepository.save(execution);

        writeLog(job, execution, LogLevel.ERROR,
                "Job failed: " + error.getMessage());

        // Check retry policy
        Queue queue = queueRepository.findById(job.getQueue().getId()).orElse(null);
        RetryPolicy retryPolicy = (queue != null) ? queue.getRetryPolicy() : null;
        int maxAttempts = job.getMaxAttempts();

        if (job.getAttemptCount() < maxAttempts) {
            // Schedule for retry
            long delay = 1000; // default 1s
            if (retryPolicy != null) {
                delay = retryPolicyEngine.calculateDelay(
                        retryPolicy.getStrategy(),
                        job.getAttemptCount(),
                        retryPolicy.getInitialDelayMs(),
                        retryPolicy.getMultiplier(),
                        retryPolicy.getMaxDelayMs());
            }

            Instant nextAttemptAt = Instant.now().plusMillis(delay);
            job.setStatus(JobStatus.RETRY_PENDING);
            job.setScheduledAt(nextAttemptAt);
            job.setClaimedAt(null);
            job.setClaimedByWorker(null);
            job.setStartedAt(null);
            job.setErrorMessage(error.getMessage());
            jobRepository.save(job);
            jobEventPublisher.publishJobUpdate(job.getId(), "RETRY_PENDING", job.getQueue().getId());

            writeLog(job, execution, LogLevel.WARN,
                    "Scheduling retry " + (job.getAttemptCount() + 1) + "/" + maxAttempts + " in " + delay + "ms");

            log.info("Job {} failed (attempt {}/{}), retrying in {}ms",
                    job.getId(), job.getAttemptCount(), maxAttempts, delay);
        } else {
            // Max attempts exhausted → Dead Letter Queue
            job.setStatus(JobStatus.DEAD_LETTER);
            job.setErrorMessage(error.getMessage());
            job.setCompletedAt(endTime);
            jobRepository.save(job);
            jobEventPublisher.publishJobUpdate(job.getId(), "DEAD_LETTER", job.getQueue().getId());

            DeadLetterEntry dlqEntry = DeadLetterEntry.builder()
                    .originalJob(job)
                    .queue(job.getQueue())
                    .payload(job.getPayload())
                    .totalAttempts(job.getAttemptCount())
                    .finalError(error.getMessage())
                    .finalStackTrace(stackTrace)
                    .failedAt(endTime)
                    .build();
            deadLetterRepository.save(dlqEntry);

            writeLog(job, execution, LogLevel.ERROR,
                    "Job moved to Dead Letter Queue after " + maxAttempts + " failed attempts");

            log.warn("Job {} moved to DLQ after {} attempts: {}",
                    job.getId(), maxAttempts, error.getMessage());
        }
    }

    private void writeLog(Job job, JobExecution execution, LogLevel level, String message) {
        try {
            JobLog jobLog = JobLog.builder()
                    .job(job)
                    .execution(execution)
                    .level(level)
                    .message(message)
                    .metadata(Map.of("workerId",
                            execution.getWorkerId() != null ? execution.getWorkerId().toString() : "unknown"))
                    .build();
            logRepository.save(jobLog);
        } catch (Exception e) {
            log.error("Failed to write job log: {}", e.getMessage());
        }
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();
        // Limit stack trace length
        return trace.length() > 4000 ? trace.substring(0, 4000) + "\n... truncated" : trace;
    }
}
