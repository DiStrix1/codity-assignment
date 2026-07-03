package com.jobscheduler;

import com.jobscheduler.domain.entity.*;
import com.jobscheduler.domain.entity.Queue;
import com.jobscheduler.domain.enums.*;
import com.jobscheduler.domain.repository.*;
import com.jobscheduler.worker.JobExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the complete Dead Letter Queue transition:
 * - A job that fails max_attempts times is moved to DEAD_LETTER status
 * - A DLQ entry is created with the final error details
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("api")
class DeadLetterTransitionTest {

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
        private RetryPolicyRepository retryPolicyRepository;
        @Autowired
        private DeadLetterRepository deadLetterRepository;
        @Autowired
        private JobExecutionRepository executionRepository;
        @Autowired
        private JobExecutor jobExecutor;

        private Queue testQueue;
        private UUID workerId;

        @BeforeEach
        void setup() {
                deadLetterRepository.deleteAll();
                executionRepository.deleteAll();
                jobRepository.deleteAll();
                queueRepository.deleteAll();

                workerId = UUID.randomUUID();

                Organization org = orgRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                                .orElseGet(() -> orgRepository.save(Organization.builder()
                                                .name("Test Org")
                                                .slug("test-org-dlq-" + UUID.randomUUID().toString().substring(0, 8))
                                                .build()));

                RetryPolicy policy = retryPolicyRepository.save(RetryPolicy.builder()
                                .name("Test Policy")
                                .strategy(RetryStrategy.FIXED)
                                .maxAttempts(3)
                                .initialDelayMs(100)
                                .multiplier(1.0)
                                .maxDelayMs(100)
                                .organization(org)
                                .build());

                Project project = projectRepository.save(Project.builder()
                                .name("DLQ Test Project")
                                .slug("dlq-test-" + UUID.randomUUID().toString().substring(0, 8))
                                .organization(org)
                                .build());

                testQueue = queueRepository.save(Queue.builder()
                                .name("DLQ Test Queue")
                                .slug("dlq-queue-" + UUID.randomUUID().toString().substring(0, 8))
                                .project(project)
                                .priority(1)
                                .maxConcurrency(5)
                                .retryPolicy(policy)
                                .paused(false)
                                .build());
        }

        @Test
        @DisplayName("Job moves to DEAD_LETTER after exhausting all attempts")
        void testDeadLetterTransition() {
                // Create a job that will always fail
                Job job = Job.builder()
                                .queue(testQueue)
                                .type(JobType.IMMEDIATE)
                                .status(JobStatus.CLAIMED)
                                .payload(Map.of("fail", true, "duration_ms", 100, "error_message",
                                                "Intentional failure"))
                                .priority(0)
                                .attemptCount(3) // Already at max
                                .maxAttempts(3)
                                .claimedByWorker(workerId)
                                .build();
                job = jobRepository.save(job);

                // Execute — should fail and move to DLQ
                jobExecutor.execute(job, workerId);

                // Verify job is in DLQ
                Job updatedJob = jobRepository.findById(job.getId()).orElseThrow();
                assertEquals(JobStatus.DEAD_LETTER, updatedJob.getStatus(),
                                "Job should be in DEAD_LETTER status after exhausting all attempts");

                // Verify DLQ entry exists
                Optional<DeadLetterEntry> dlqEntry = deadLetterRepository.findByOriginalJobId(job.getId());
                assertTrue(dlqEntry.isPresent(), "A DLQ entry should have been created");
                assertEquals(3, dlqEntry.get().getTotalAttempts());
                assertNotNull(dlqEntry.get().getFinalError());
                assertNotNull(dlqEntry.get().getFinalStackTrace());

                System.out.println("✅ DEAD LETTER TRANSITION TEST PASSED");
                System.out.println("   Final error: " + dlqEntry.get().getFinalError());
        }

        @Test
        @DisplayName("Job with remaining attempts is re-queued, not sent to DLQ")
        void testRetryBeforeDLQ() {
                // Create a job that fails but has remaining attempts
                Job job = Job.builder()
                                .queue(testQueue)
                                .type(JobType.IMMEDIATE)
                                .status(JobStatus.CLAIMED)
                                .payload(Map.of("fail", true, "duration_ms", 100))
                                .priority(0)
                                .attemptCount(1) // Attempt 1 of 3
                                .maxAttempts(3)
                                .claimedByWorker(workerId)
                                .build();
                job = jobRepository.save(job);

                // Execute — should fail but re-queue
                jobExecutor.execute(job, workerId);

                // Verify job is in RETRY_PENDING (not DLQ)
                Job updatedJob = jobRepository.findById(job.getId()).orElseThrow();
                assertEquals(JobStatus.RETRY_PENDING, updatedJob.getStatus(),
                                "Job should be in RETRY_PENDING for retry, not in DLQ");

                // Verify no DLQ entry
                Optional<DeadLetterEntry> dlqEntry = deadLetterRepository.findByOriginalJobId(job.getId());
                assertFalse(dlqEntry.isPresent(), "No DLQ entry should exist for a retryable job");

                System.out.println("✅ RETRY BEFORE DLQ TEST PASSED");
        }
}
