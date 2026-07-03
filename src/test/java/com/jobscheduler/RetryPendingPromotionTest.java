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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("api")
class RetryPendingPromotionTest {

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

    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private QueueRepository queueRepository;
    @Autowired
    private OrganizationRepository orgRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Queue testQueue;

    @BeforeEach
    void setup() {
        jobRepository.deleteAll();
        queueRepository.deleteAll();
        projectRepository.deleteAll();

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
                .maxConcurrency(100)
                .paused(false)
                .build());
    }

    @Test
    @DisplayName("Concurrently promote 50 RETRY_PENDING jobs: ensure race-safe and exactly once promotion")
    void testConcurrentRetryPromotion() throws Exception {
        final int JOB_COUNT = 50;
        final int PROMOTER_COUNT = 5;

        // Create 50 jobs with RETRY_PENDING in the past
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < JOB_COUNT; i++) {
            Job job = Job.builder()
                    .queue(testQueue)
                    .type(JobType.IMMEDIATE)
                    .status(JobStatus.RETRY_PENDING)
                    .payload(Map.of("index", i))
                    .priority(0)
                    .maxAttempts(3)
                    .scheduledAt(Instant.now().minusSeconds(10)) // past due
                    .build();
            jobs.add(job);
        }
        jobRepository.saveAll(jobs);

        assertEquals(JOB_COUNT, jobRepository.countByQueueIdAndStatus(testQueue.getId(), JobStatus.RETRY_PENDING));

        // Let 5 threads concurrently attempt to promote the jobs
        ExecutorService executor = Executors.newFixedThreadPool(PROMOTER_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(PROMOTER_COUNT);
        AtomicInteger totalPromotedCount = new AtomicInteger(0);

        for (int p = 0; p < PROMOTER_COUNT; p++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Integer promoted = transactionTemplate.execute(status -> jobRepository.promoteRetryingJobs());
                    if (promoted != null) {
                        totalPromotedCount.addAndGet(promoted);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "Promotion threads must complete");
        executor.shutdown();

        // Assertions
        assertEquals(JOB_COUNT, totalPromotedCount.get(), "Sum of all promotions must equal exact job count");
        long remainingRetryPending = jobRepository.countByQueueIdAndStatus(testQueue.getId(), JobStatus.RETRY_PENDING);
        assertEquals(0, remainingRetryPending, "No jobs should remain in RETRY_PENDING");
        long queuedCount = jobRepository.countByQueueIdAndStatus(testQueue.getId(), JobStatus.QUEUED);
        assertEquals(JOB_COUNT, queuedCount, "All jobs should now be in QUEUED state");

        System.out.println("✅ CONCURRENT RETRY PROMOTION TEST PASSED");
    }
}
