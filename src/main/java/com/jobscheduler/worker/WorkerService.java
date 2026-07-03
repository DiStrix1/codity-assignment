package com.jobscheduler.worker;

import com.jobscheduler.domain.entity.Job;
import com.jobscheduler.domain.entity.Queue;
import com.jobscheduler.domain.entity.Worker;
import com.jobscheduler.domain.entity.WorkerHeartbeat;
import com.jobscheduler.domain.enums.JobStatus;
import com.jobscheduler.domain.enums.WorkerStatus;
import com.jobscheduler.domain.repository.*;
import com.jobscheduler.websocket.JobEventPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The main worker service that orchestrates job claiming, execution, and
 * heartbeats.
 *
 * Lifecycle:
 * 1. On startup: registers itself in the workers table
 * 2. Starts a polling loop that claims jobs from queues (respecting priority +
 * concurrency)
 * 3. Starts a heartbeat loop (every 15s)
 * 4. On shutdown: drains in-flight jobs, deregisters
 *
 * Activated only when the 'worker' Spring profile is active.
 */
@Service
@Profile("worker")
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;
    private final WorkerRepository workerRepository;
    private final WorkerHeartbeatRepository heartbeatRepository;
    private final JobExecutor jobExecutor;
    private final JobEventPublisher jobEventPublisher;

    @Value("${worker.id:#{T(java.util.UUID).randomUUID().toString()}}")
    private String workerIdStr;

    @Value("${worker.poll-interval-ms:500}")
    private long pollIntervalMs;

    @Value("${worker.heartbeat-interval-ms:15000}")
    private long heartbeatIntervalMs;

    @Value("${worker.stale-worker-threshold-ms:60000}")
    private long staleWorkerThresholdMs;

    @Value("${worker.max-concurrent-jobs:10}")
    private int maxConcurrentJobs;

    @Value("${worker.shutdown-timeout-seconds:30}")
    private int shutdownTimeoutSeconds;

    private UUID workerId;
    private ExecutorService jobExecutorPool;
    private ScheduledExecutorService schedulerPool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger activeJobCount = new AtomicInteger(0);
    private final ConcurrentHashMap<UUID, Semaphore> queueSemaphores = new ConcurrentHashMap<>();

    private final TransactionTemplate transactionTemplate;

    public WorkerService(JobRepository jobRepository,
            QueueRepository queueRepository,
            WorkerRepository workerRepository,
            WorkerHeartbeatRepository heartbeatRepository,
            JobExecutor jobExecutor,
            TransactionTemplate transactionTemplate,
            JobEventPublisher jobEventPublisher) {
        this.jobRepository = jobRepository;
        this.queueRepository = queueRepository;
        this.workerRepository = workerRepository;
        this.heartbeatRepository = heartbeatRepository;
        this.jobExecutor = jobExecutor;
        this.transactionTemplate = transactionTemplate;
        this.jobEventPublisher = jobEventPublisher;
    }

    @PostConstruct
    public void start() {
        // Register worker
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }

        UUID id = null;
        java.io.File idFile = new java.io.File("worker-id-" + hostname + ".txt");
        if (idFile.exists()) {
            try {
                String content = java.nio.file.Files.readString(idFile.toPath()).trim();
                id = UUID.fromString(content);
                log.info("Loaded persisted worker ID {} from {}", id, idFile.getName());
            } catch (Exception e) {
                log.warn("Failed to read persisted worker ID from {}, generating a new one", idFile.getName(), e);
            }
        }
        if (id == null) {
            id = UUID.randomUUID();
            try {
                java.nio.file.Files.writeString(idFile.toPath(), id.toString());
                log.info("Persisted new worker ID {} to {}", id, idFile.getName());
            } catch (Exception e) {
                log.error("Failed to persist worker ID", e);
            }
        }
        this.workerId = id;

        Worker worker = workerRepository.findById(workerId).orElse(null);
        if (worker != null) {
            worker.setStatus(WorkerStatus.ACTIVE);
            worker.setCurrentJobCount(0);
            worker.setLastHeartbeatAt(Instant.now());
            worker.setMaxConcurrentJobs(maxConcurrentJobs);
        } else {
            worker = Worker.builder()
                    .id(workerId)
                    .hostname(hostname)
                    .status(WorkerStatus.ACTIVE)
                    .maxConcurrentJobs(maxConcurrentJobs)
                    .currentJobCount(0)
                    .registeredAt(Instant.now())
                    .lastHeartbeatAt(Instant.now())
                    .build();
        }
        workerRepository.save(worker);
        jobEventPublisher.publishWorkerUpdate(workerId, "ACTIVE", 0);

        log.info("Worker {} registered on host {}", workerId, hostname);

        // Initialize executors (Java 17 compatible)
        jobExecutorPool = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "worker-thread-" + count.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        schedulerPool = Executors.newScheduledThreadPool(2, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "worker-scheduler-" + count.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        running.set(true);

        // Start polling loop
        schedulerPool.scheduleWithFixedDelay(this::pollAndClaim,
                1000, pollIntervalMs, TimeUnit.MILLISECONDS);

        // Start heartbeat loop
        schedulerPool.scheduleWithFixedDelay(this::sendHeartbeat,
                5000, heartbeatIntervalMs, TimeUnit.MILLISECONDS);

        // Start scheduled job promotion loop
        schedulerPool.scheduleWithFixedDelay(this::promoteScheduledJobs,
                2000, 5000, TimeUnit.MILLISECONDS);

        // Start stale worker recovery loop
        schedulerPool.scheduleWithFixedDelay(this::recoverStaleWorkers,
                30000, 30000, TimeUnit.MILLISECONDS);

        log.info("Worker {} started with max {} concurrent jobs, poll interval {}ms",
                workerId, maxConcurrentJobs, pollIntervalMs);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Worker {} shutting down — draining {} in-flight jobs...", workerId, activeJobCount.get());
        running.set(false);

        // Mark as draining
        workerRepository.findById(workerId).ifPresent(w -> {
            w.setStatus(WorkerStatus.DRAINING);
            workerRepository.save(w);
            jobEventPublisher.publishWorkerUpdate(workerId, "DRAINING", activeJobCount.get());
        });

        // Stop accepting new work
        schedulerPool.shutdown();

        // Wait for in-flight jobs to complete
        jobExecutorPool.shutdown();
        try {
            if (!jobExecutorPool.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("Force-shutting down {} jobs that didn't complete in {}s",
                        activeJobCount.get(), shutdownTimeoutSeconds);
                jobExecutorPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            jobExecutorPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Deregister
        workerRepository.findById(workerId).ifPresent(w -> {
            w.setStatus(WorkerStatus.OFFLINE);
            w.setCurrentJobCount(0);
            workerRepository.save(w);
            jobEventPublisher.publishWorkerUpdate(workerId, "OFFLINE", 0);
        });

        log.info("Worker {} shutdown complete", workerId);
    }

    /**
     * Main polling loop: iterates through active queues by priority,
     * claims one job at a time from each queue, respecting concurrency limits.
     */
    private void pollAndClaim() {
        if (!running.get())
            return;
        if (activeJobCount.get() >= maxConcurrentJobs)
            return;

        try {
            List<Queue> activeQueues = queueRepository.findActiveQueuesOrderedByPriority();

            for (Queue queue : activeQueues) {
                if (!running.get() || activeJobCount.get() >= maxConcurrentJobs)
                    break;

                // Check queue-level concurrency
                Semaphore queueSem = queueSemaphores.computeIfAbsent(
                        queue.getId(), k -> new Semaphore(queue.getMaxConcurrency()));

                if (!queueSem.tryAcquire())
                    continue;

                // Also check actual running count in DB
                int runningInQueue = jobRepository.countRunningJobsInQueue(queue.getId());
                if (runningInQueue >= queue.getMaxConcurrency()) {
                    queueSem.release();
                    continue;
                }

                try {
                    claimAndExecute(queue, queueSem);
                } catch (Exception e) {
                    queueSem.release();
                    log.error("Error claiming job from queue {}: {}", queue.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in poll loop: {}", e.getMessage(), e);
        }
    }

    protected void claimAndExecute(Queue queue, Semaphore queueSem) {
        // Atomic claim with SKIP LOCKED
        Integer claimed = transactionTemplate.execute(status -> jobRepository.claimNextJob(queue.getId(), workerId));

        if (claimed == null || claimed == 0) {
            queueSem.release();
            return;
        }

        // Find the job we just claimed
        List<Job> claimedJobs = transactionTemplate.execute(status -> jobRepository.findClaimedByWorker(workerId));
        if (claimedJobs == null || claimedJobs.isEmpty()) {
            queueSem.release();
            return;
        }

        Job job = claimedJobs.get(0);
        activeJobCount.incrementAndGet();

        log.debug("Claimed job {} from queue {} (attempt {})",
                job.getId(), queue.getName(), job.getAttemptCount());

        // Execute on thread
        jobExecutorPool.submit(() -> {
            try {
                jobExecutor.execute(job, workerId);
            } catch (Exception e) {
                log.error("Unexpected error executing job {}: {}", job.getId(), e.getMessage(), e);
            } finally {
                activeJobCount.decrementAndGet();
                queueSem.release();
                updateWorkerJobCount();
            }
        });

        updateWorkerJobCount();
    }

    private void sendHeartbeat() {
        try {
            Instant now = Instant.now();
            int jobCount = activeJobCount.get();

            transactionTemplate.executeWithoutResult(status -> {
                workerRepository.updateHeartbeat(workerId, now, jobCount);

                Runtime runtime = Runtime.getRuntime();
                long memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
                double cpuLoad = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();

                WorkerHeartbeat heartbeat = WorkerHeartbeat.builder()
                        .worker(Worker.builder().id(workerId).build())
                        .activeJobs(jobCount)
                        .memoryUsedMb(memoryUsedMb)
                        .cpuLoad(cpuLoad >= 0 ? cpuLoad : null)
                        .build();
                heartbeatRepository.save(heartbeat);
            });
            jobEventPublisher.publishWorkerUpdate(workerId, "ACTIVE", jobCount);

            log.trace("Heartbeat sent: {} active jobs", jobCount);
        } catch (Exception e) {
            log.error("Failed to send heartbeat: {}", e.getMessage());
        }
    }

    /**
     * Promotes SCHEDULED jobs whose scheduled_at has passed to QUEUED status.
     */
    protected void promoteScheduledJobs() {
        try {
            Integer promoted = transactionTemplate.execute(status -> jobRepository.promoteScheduledJobs());
            if (promoted != null && promoted > 0) {
                log.info("Promoted {} scheduled jobs to QUEUED", promoted);
            }
            Integer retryingPromoted = transactionTemplate.execute(status -> jobRepository.promoteRetryingJobs());
            if (retryingPromoted != null && retryingPromoted > 0) {
                log.info("Promoted {} retrying jobs to QUEUED", retryingPromoted);
            }
        } catch (Exception e) {
            log.error("Error promoting scheduled/retrying jobs: {}", e.getMessage());
        }
    }

    /**
     * Detects stale workers (no heartbeat for > threshold) and re-queues their
     * claimed jobs.
     */
    protected void recoverStaleWorkers() {
        try {
            Instant threshold = Instant.now().minusMillis(staleWorkerThresholdMs);

            transactionTemplate.executeWithoutResult(status -> {
                // Mark stale workers offline
                int staleCount = workerRepository.markStaleWorkersOffline(threshold);
                if (staleCount > 0) {
                    log.warn("Marked {} stale workers as OFFLINE", staleCount);
                }

                // Re-queue their claimed jobs
                int requeued = jobRepository.requeueStaleClaims(threshold);
                if (requeued > 0) {
                    log.warn("Re-queued {} jobs from stale workers", requeued);
                }
            });
        } catch (Exception e) {
            log.error("Error recovering stale workers: {}", e.getMessage());
        }
    }

    private void updateWorkerJobCount() {
        try {
            transactionTemplate.executeWithoutResult(
                    status -> workerRepository.updateHeartbeat(workerId, Instant.now(), activeJobCount.get()));
            jobEventPublisher.publishWorkerUpdate(workerId, "ACTIVE", activeJobCount.get());
        } catch (Exception e) {
            // Non-critical, will be updated on next heartbeat
        }
    }
}
