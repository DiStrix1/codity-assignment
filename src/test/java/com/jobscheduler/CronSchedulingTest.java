package com.jobscheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.jobscheduler.domain.entity.*;
import com.jobscheduler.domain.enums.JobStatus;
import com.jobscheduler.domain.enums.JobType;
import com.jobscheduler.domain.repository.*;
import com.jobscheduler.worker.CronSchedulerService;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles({"api", "worker"})
class CronSchedulingTest {

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

    @Autowired private ScheduledJobRepository scheduledJobRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private QueueRepository queueRepository;
    @Autowired private OrganizationRepository orgRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private CronSchedulerService cronSchedulerService;

    private Queue testQueue;
    private final CronParser cronParser = new CronParser(
            CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    @BeforeEach
    void setup() {
        scheduledJobRepository.deleteAll();
        jobRepository.deleteAll();
        queueRepository.deleteAll();

        Organization org = orgRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .orElseGet(() -> orgRepository.save(Organization.builder()
                        .name("Test Org")
                        .slug("test-org-cron-" + UUID.randomUUID().toString().substring(0, 8))
                        .build()));

        Project project = projectRepository.save(Project.builder()
                .name("Cron Test Project")
                .slug("cron-test-" + UUID.randomUUID().toString().substring(0, 8))
                .organization(org)
                .build());

        testQueue = queueRepository.save(Queue.builder()
                .name("Cron Test Queue")
                .slug("cron-queue-" + UUID.randomUUID().toString().substring(0, 8))
                .project(project)
                .priority(1)
                .maxConcurrency(5)
                .paused(false)
                .build());
    }

    @Test
    @DisplayName("CronSchedulerService triggers due scheduled jobs and calculates next fire time correctly")
    void testCronSchedulingTrigger() {
        // 1. Create a scheduled job with cron expression (run every minute)
        String cronExpr = "*/1 * * * *"; // Every minute
        
        Cron cron = cronParser.parse(cronExpr);
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime nowZoned = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime nextExpectedFire = executionTime.nextExecution(nowZoned).orElseThrow();

        // Save a scheduled job that is already due (set nextFireAt in the past)
        ScheduledJob scheduledJob = ScheduledJob.builder()
                .queue(testQueue)
                .cronExpression(cronExpr)
                .payload(Map.of("message", "cron-run"))
                .enabled(true)
                .nextFireAt(Instant.now().minusSeconds(10)) // 10s in past -> due!
                .build();
        scheduledJob = scheduledJobRepository.save(scheduledJob);

        // 2. Trigger scheduler
        cronSchedulerService.triggerDueScheduledJobs();

        // 3. Assert a regular job was created
        List<Job> createdJobs = jobRepository.findAll();
        assertEquals(1, createdJobs.size(), "One job should have been created");
        Job job = createdJobs.get(0);
        assertEquals(JobType.RECURRING, job.getType());
        assertEquals(JobStatus.QUEUED, job.getStatus());
        assertEquals("cron-run", job.getPayload().get("message"));

        // 4. Assert scheduled job next fire time was updated to a future time
        ScheduledJob updatedScheduled = scheduledJobRepository.findById(scheduledJob.getId()).orElseThrow();
        assertTrue(updatedScheduled.getNextFireAt().isAfter(Instant.now()), 
                "Next fire time should have been recalculated to a future time");
        
        // Next fire time should correspond to nextExpectedFire approximately
        long diffSeconds = Math.abs(updatedScheduled.getNextFireAt().getEpochSecond() - nextExpectedFire.toInstant().getEpochSecond());
        assertTrue(diffSeconds <= 60, "Next fire time should match expected cron time (diff: " + diffSeconds + "s)");
        assertNotNull(updatedScheduled.getLastTriggeredAt());

        System.out.println("✅ CRON SCHEDULING TEST PASSED");
    }
}
