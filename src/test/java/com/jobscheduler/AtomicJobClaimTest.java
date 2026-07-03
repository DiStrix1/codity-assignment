package com.jobscheduler;

import com.jobscheduler.domain.entity.*;
import com.jobscheduler.domain.entity.Queue;
import com.jobscheduler.domain.enums.*;
import com.jobscheduler.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NON-NEGOTIABLE TEST: Proves that no two workers can claim the same job.
 *
 * Setup:
 * - Testcontainers PostgreSQL (real database)
 * - 100 jobs in a single queue
 * - 10 concurrent worker threads
 *
 * Assertion: Every job claimed exactly once. No duplicates. No misses.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("api") // Don't start the real worker service
class AtomicJobClaimTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jobscheduler_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private JobRepository jobRepository;
    @Autowired private QueueRepository queueRepository;
    @Autowired private OrganizationRepository orgRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private Queue testQueue;

    @BeforeEach
    void setup() {
        // Clean existing data
        jobRepository.deleteAll();
        queueRepository.deleteAll();
        projectRepository.deleteAll();

        // Create test hierarchy: org → project → queue
        Organization org = orgRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .orElseGet(() -> orgRepository.save(Organization.builder()
                        .name("Test Org")
                        .slug("test-org-" + UUID.randomUUID().toString().substring(0, 8))
                        .build()));

        Project project = projectRepository.save(Project.builder()
                .name("Test Project")
                .slug("test-project-" + UUID.randomUUID().toString().substring(0, 8))
                .organization(org)
                .build());

        testQueue = queueRepository.save(Queue.builder()
                .name("Test Queue")
                .slug("test-queue-" + UUID.randomUUID().toString().substring(0, 8))
                .project(project)
                .priority(1)
                .maxConcurrency(100) // Allow all to run
                .paused(false)
                .build());
    }

    @Test
    @DisplayName("100 jobs, 10 concurrent workers: each job must be claimed exactly once")
    void testAtomicJobClaimUnderConcurrency() throws Exception {
        final int JOB_COUNT = 100;
        final int WORKER_COUNT = 10;

        // Create 100 jobs
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < JOB_COUNT; i++) {
            Job job = Job.builder()
                    .queue(testQueue)
                    .type(JobType.IMMEDIATE)
                    .status(JobStatus.QUEUED)
                    .payload(Map.of("index", i))
                    .priority(0)
                    .maxAttempts(3)
                    .build();
            jobs.add(job);
        }
        jobRepository.saveAll(jobs);
        assertEquals(JOB_COUNT, jobRepository.countByQueueIdAndStatus(testQueue.getId(), JobStatus.QUEUED));

        // Track which worker claimed which jobs
        ConcurrentHashMap<UUID, List<UUID>> claimedByWorker = new ConcurrentHashMap<>();
        Set<UUID> allClaimedJobIds = ConcurrentHashMap.newKeySet();
        AtomicInteger totalClaims = new AtomicInteger(0);

        // Spin up 10 concurrent worker threads
        ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);  // Ensures all start simultaneously
        CountDownLatch doneLatch = new CountDownLatch(WORKER_COUNT);

        for (int w = 0; w < WORKER_COUNT; w++) {
            UUID workerId = UUID.randomUUID();
            claimedByWorker.put(workerId, Collections.synchronizedList(new ArrayList<>()));

            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for GO signal

                    while (true) {
                        Integer claimed = transactionTemplate.execute(status ->
                            jobRepository.claimNextJob(testQueue.getId(), workerId)
                        );
                        if (claimed == null || claimed == 0) break; // No more jobs

                        totalClaims.incrementAndGet();

                        // Find what we claimed and update within a transaction
                        transactionTemplate.executeWithoutResult(status -> {
                            List<Job> myClaims = jobRepository.findClaimedByWorker(workerId);
                            for (Job j : myClaims) {
                                claimedByWorker.get(workerId).add(j.getId());
                                allClaimedJobIds.add(j.getId());
                                // Move to a terminal state so we don't find it again
                                j.setStatus(JobStatus.RUNNING);
                                jobRepository.save(j);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // GO!
        startLatch.countDown();

        // Wait for all workers to finish
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Workers should complete within 30 seconds");
        executor.shutdown();

        // ─── ASSERTIONS ──────────────────────────────────────────────────────

        // 1. Every job was claimed exactly once
        assertEquals(JOB_COUNT, allClaimedJobIds.size(),
                "Every job should be claimed exactly once — got " + allClaimedJobIds.size() + " unique claims");

        // 2. Total claims equals job count (no duplicates)
        assertEquals(JOB_COUNT, totalClaims.get(),
                "Total claim count should equal job count — got " + totalClaims.get());

        // 3. No remaining QUEUED jobs
        long remainingQueued = jobRepository.countByQueueIdAndStatus(testQueue.getId(), JobStatus.QUEUED);
        assertEquals(0, remainingQueued, "No jobs should remain in QUEUED state");

        // 4. Work was distributed across multiple workers
        long workersWithJobs = claimedByWorker.values().stream().filter(l -> !l.isEmpty()).count();
        assertTrue(workersWithJobs > 1,
                "Jobs should be distributed across multiple workers, but only " + workersWithJobs + " got work");

        System.out.println("✅ ATOMIC CLAIM TEST PASSED");
        System.out.println("   Jobs: " + JOB_COUNT + ", Workers: " + WORKER_COUNT);
        System.out.println("   Unique claims: " + allClaimedJobIds.size());
        System.out.println("   Workers with jobs: " + workersWithJobs);
        claimedByWorker.forEach((wid, jids) -> {
            if (!jids.isEmpty()) {
                System.out.println("   Worker " + wid.toString().substring(0, 8) + ": " + jids.size() + " jobs");
            }
        });
    }
}
