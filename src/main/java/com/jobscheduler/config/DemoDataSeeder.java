package com.jobscheduler.config;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.jobscheduler.domain.entity.*;
import com.jobscheduler.domain.enums.JobStatus;
import com.jobscheduler.domain.enums.JobType;
import com.jobscheduler.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Automatically seeds the database with projects, queues, and mock jobs on startup
 * if the database is empty, to provide a rich out-of-the-box demo experience.
 */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final QueueRepository queueRepository;
    private final RetryPolicyRepository retryPolicyRepository;
    private final JobRepository jobRepository;
    private final ScheduledJobRepository scheduledJobRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final JobExecutionRepository executionRepository;

    private final CronParser cronParser = new CronParser(
            CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    public DemoDataSeeder(OrganizationRepository organizationRepository,
                          ProjectRepository projectRepository,
                          QueueRepository queueRepository,
                          RetryPolicyRepository retryPolicyRepository,
                          JobRepository jobRepository,
                          ScheduledJobRepository scheduledJobRepository,
                          DeadLetterRepository deadLetterRepository,
                          JobExecutionRepository executionRepository) {
        this.organizationRepository = organizationRepository;
        this.projectRepository = projectRepository;
        this.queueRepository = queueRepository;
        this.retryPolicyRepository = retryPolicyRepository;
        this.jobRepository = jobRepository;
        this.scheduledJobRepository = scheduledJobRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.executionRepository = executionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (projectRepository.count() > 0) {
            log.info("Database already contains project data. Skipping demo data seeding.");
            return;
        }

        log.info("Database is empty. Seeding demo projects, queues, and jobs for dashboard display...");

        // 1. Fetch Default Org (Seeded by Flyway)
        Organization org = organizationRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .orElseThrow(() -> new IllegalStateException("Default organization not found in database!"));

        // 2. Fetch Policies (Seeded by Flyway)
        RetryPolicy expPolicy = retryPolicyRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000004"))
                .orElse(null);
        RetryPolicy fixedPolicy = retryPolicyRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .orElse(null);

        // 3. Create Demo Projects
        Project project1 = Project.builder()
                .name("E-Commerce Storefront")
                .slug("ecommerce")
                .organization(org)
                .build();
        project1 = projectRepository.save(project1);

        Project project2 = Project.builder()
                .name("Data Analytics Pipeline")
                .slug("analytics")
                .organization(org)
                .build();
        project2 = projectRepository.save(project2);

        // 4. Create Queues for E-Commerce Project
        Queue paymentQueue = Queue.builder()
                .name("Payment Processing")
                .slug("payments")
                .project(project1)
                .priority(10) // High priority
                .maxConcurrency(3)
                .retryPolicy(expPolicy)
                .paused(false)
                .build();
        paymentQueue = queueRepository.save(paymentQueue);

        Queue notificationQueue = Queue.builder()
                .name("Email Notifications")
                .slug("notifications")
                .project(project1)
                .priority(3)
                .maxConcurrency(5)
                .retryPolicy(fixedPolicy)
                .paused(false)
                .build();
        notificationQueue = queueRepository.save(notificationQueue);

        Queue reportQueue = Queue.builder()
                .name("Report Generation")
                .slug("reports")
                .project(project2)
                .priority(1) // Low priority
                .maxConcurrency(2)
                .paused(false)
                .build();
        reportQueue = queueRepository.save(reportQueue);

        // 5. Seed Jobs in Payment Queue
        // Complete Job
        Job completedJob = Job.builder()
                .queue(paymentQueue)
                .type(JobType.IMMEDIATE)
                .status(JobStatus.COMPLETED)
                .payload(Map.of("order_id", "ORD-98213", "amount", 129.99, "currency", "USD"))
                .priority(0)
                .attemptCount(1)
                .maxAttempts(3)
                .startedAt(Instant.now().minusSeconds(3600))
                .completedAt(Instant.now().minusSeconds(3598))
                .build();
        jobRepository.save(completedJob);

        // Queued Jobs (ready to be picked up by Worker)
        for (int i = 1; i <= 3; i++) {
            Job queuedJob = Job.builder()
                    .queue(paymentQueue)
                    .type(JobType.IMMEDIATE)
                    .status(JobStatus.QUEUED)
                    .payload(Map.of("order_id", "ORD-DEMO-00" + i, "amount", 19.99 * i, "duration_ms", 2000))
                    .priority(5)
                    .maxAttempts(3)
                    .build();
            jobRepository.save(queuedJob);
        }

        // 6. Seed Jobs in Notification Queue
        // Failed Job
        Job failedJob = Job.builder()
                .queue(notificationQueue)
                .type(JobType.IMMEDIATE)
                .status(JobStatus.FAILED)
                .payload(Map.of("recipient", "user@example.com", "template", "welcome_email"))
                .priority(0)
                .attemptCount(2)
                .maxAttempts(3)
                .errorMessage("SMTP Server connection timeout")
                .startedAt(Instant.now().minusSeconds(1800))
                .completedAt(Instant.now().minusSeconds(1799))
                .build();
        jobRepository.save(failedJob);

        // Queued Notification Job
        Job queuedNotif = Job.builder()
                .queue(notificationQueue)
                .type(JobType.IMMEDIATE)
                .status(JobStatus.QUEUED)
                .payload(Map.of("recipient", "admin@store.com", "subject", "Daily Sales Summary", "duration_ms", 500))
                .priority(0)
                .maxAttempts(3)
                .build();
        jobRepository.save(queuedNotif);

        // 7. Seed DLQ Job
        Job dlqJob = Job.builder()
                .queue(paymentQueue)
                .type(JobType.IMMEDIATE)
                .status(JobStatus.DEAD_LETTER)
                .payload(Map.of("order_id", "ORD-FAIL-666", "amount", 999.99, "card_token", "invalid_tok"))
                .priority(0)
                .attemptCount(3)
                .maxAttempts(3)
                .errorMessage("Card authorization declined permanently")
                .startedAt(Instant.now().minusSeconds(7200))
                .completedAt(Instant.now().minusSeconds(7198))
                .build();
        dlqJob = jobRepository.save(dlqJob);

        // Create the corresponding DLQ entry
        DeadLetterEntry dlqEntry = DeadLetterEntry.builder()
                .originalJob(dlqJob)
                .queue(paymentQueue)
                .payload(dlqJob.getPayload())
                .totalAttempts(3)
                .finalError("Card authorization declined permanently")
                .finalStackTrace("CardDeclinedException: Insufficient funds / permanent lock\n  at com.jobscheduler.handler.PaymentHandler.authorize(PaymentHandler.java:42)\n  at com.jobscheduler.worker.JobExecutor.execute(JobExecutor.java:112)")
                .failedAt(Instant.now().minusSeconds(7198))
                .build();
        deadLetterRepository.save(dlqEntry);

        // 8. Seed Recurring / Cron Scheduled Job
        String cronExpr = "*/1 * * * *"; // Every minute
        Cron cron = cronParser.parse(cronExpr);
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime nextFire = executionTime.nextExecution(ZonedDateTime.now(ZoneId.of("UTC")))
                .orElse(ZonedDateTime.now(ZoneId.of("UTC")).plusMinutes(1));

        ScheduledJob scheduledJob = ScheduledJob.builder()
                .queue(notificationQueue)
                .cronExpression(cronExpr)
                .payload(Map.of("task", "send_newsletter", "campaign_id", "newsletter_v1", "duration_ms", 1000))
                .enabled(true)
                .nextFireAt(nextFire.toInstant())
                .build();
        scheduledJobRepository.save(scheduledJob);

        // Create the initial scheduled instance of it in jobs table
        Job initialScheduledJob = Job.builder()
                .queue(notificationQueue)
                .type(JobType.RECURRING)
                .status(JobStatus.SCHEDULED)
                .payload(scheduledJob.getPayload())
                .priority(0)
                .maxAttempts(3)
                .scheduledAt(nextFire.toInstant())
                .build();
        jobRepository.save(initialScheduledJob);

        log.info("Demo data seeded successfully! 2 projects, 3 queues, and 8 initial jobs are ready.");
    }
}
