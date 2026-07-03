package com.jobscheduler.worker;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.jobscheduler.domain.entity.Job;
import com.jobscheduler.domain.entity.ScheduledJob;
import com.jobscheduler.domain.enums.JobStatus;
import com.jobscheduler.domain.enums.JobType;
import com.jobscheduler.domain.repository.JobRepository;
import com.jobscheduler.domain.repository.ScheduledJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Polls the scheduled_jobs table for due entries and creates job instances.
 * Uses pessimistic locking to prevent duplicate triggers across instances.
 */
@Service
@Profile("worker")
public class CronSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(CronSchedulerService.class);

    private final ScheduledJobRepository scheduledJobRepository;
    private final JobRepository jobRepository;

    private final CronParser cronParser = new CronParser(
            CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    public CronSchedulerService(ScheduledJobRepository scheduledJobRepository,
                                 JobRepository jobRepository) {
        this.scheduledJobRepository = scheduledJobRepository;
        this.jobRepository = jobRepository;
    }

    @Scheduled(fixedDelayString = "${cron-scheduler.poll-interval-ms:10000}")
    @Transactional
    public void triggerDueScheduledJobs() {
        try {
            Instant now = Instant.now();
            List<ScheduledJob> dueJobs = scheduledJobRepository.findDueScheduledJobsForUpdate(now);

            for (ScheduledJob scheduled : dueJobs) {
                try {
                    // Create a new job instance
                    Job job = Job.builder()
                            .queue(scheduled.getQueue())
                            .type(JobType.RECURRING)
                            .status(JobStatus.QUEUED)
                            .payload(scheduled.getPayload() != null ? scheduled.getPayload() : Map.of())
                            .priority(0)
                            .maxAttempts(scheduled.getQueue().getRetryPolicy() != null
                                    ? scheduled.getQueue().getRetryPolicy().getMaxAttempts() : 3)
                            .build();
                    jobRepository.save(job);

                    // Update scheduled job with next fire time
                    scheduled.setLastTriggeredAt(now);

                    Cron cron = cronParser.parse(scheduled.getCronExpression());
                    ExecutionTime executionTime = ExecutionTime.forCron(cron);
                    ZonedDateTime nextFire = executionTime
                            .nextExecution(ZonedDateTime.now(ZoneId.of("UTC")))
                            .orElse(null);

                    if (nextFire != null) {
                        scheduled.setNextFireAt(nextFire.toInstant());
                    } else {
                        // No more future executions — disable
                        scheduled.setEnabled(false);
                        log.info("Scheduled job {} has no future executions, disabling", scheduled.getId());
                    }

                    scheduledJobRepository.save(scheduled);
                    log.debug("Triggered scheduled job {}, next fire at {}", scheduled.getId(), nextFire);

                } catch (Exception e) {
                    log.error("Failed to trigger scheduled job {}: {}", scheduled.getId(), e.getMessage());
                }
            }

            if (!dueJobs.isEmpty()) {
                log.info("Triggered {} scheduled jobs", dueJobs.size());
            }
        } catch (Exception e) {
            log.error("Error in cron scheduler polling: {}", e.getMessage(), e);
        }
    }
}
