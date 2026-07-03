package com.jobscheduler.service;

import com.jobscheduler.api.dto.MetricsDto;
import com.jobscheduler.domain.enums.ExecutionStatus;
import com.jobscheduler.domain.enums.JobStatus;
import com.jobscheduler.domain.enums.WorkerStatus;
import com.jobscheduler.domain.repository.JobExecutionRepository;
import com.jobscheduler.domain.repository.JobRepository;
import com.jobscheduler.domain.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class MetricsService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final WorkerRepository workerRepository;

    public MetricsService(JobRepository jobRepository,
                          JobExecutionRepository executionRepository,
                          WorkerRepository workerRepository) {
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.workerRepository = workerRepository;
    }

    @Transactional(readOnly = true)
    public MetricsDto getGlobalMetrics() {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

        long pending = jobRepository.countByStatus(JobStatus.QUEUED);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long deadLetter = jobRepository.countByStatus(JobStatus.DEAD_LETTER);
        long completedLastHour = jobRepository.countCompletedSince(oneHourAgo);
        long failedLastHour = jobRepository.countFailedSince(oneHourAgo);
        Double avgExecutionTime = executionRepository.avgDurationSince(oneHourAgo);
        int activeWorkers = workerRepository.findByStatus(WorkerStatus.ACTIVE).size();

        double successRate = (completedLastHour + failedLastHour) > 0
                ? (double) completedLastHour / (completedLastHour + failedLastHour) * 100.0
                : 100.0;

        return MetricsDto.builder()
                .totalJobs(pending + running + completed + failed + deadLetter)
                .pendingJobs(pending)
                .runningJobs(running)
                .completedJobs(completed)
                .failedJobs(failed)
                .deadLetterJobs(deadLetter)
                .completedLastHour(completedLastHour)
                .failedLastHour(failedLastHour)
                .avgExecutionTimeMs(avgExecutionTime)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .activeWorkers(activeWorkers)
                .build();
    }

    @Transactional(readOnly = true)
    public MetricsDto getQueueMetrics(UUID queueId) {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

        long pending = jobRepository.countByQueueIdAndStatus(queueId, JobStatus.QUEUED);
        long running = jobRepository.countByQueueIdAndStatus(queueId, JobStatus.RUNNING);
        long completed = jobRepository.countByQueueIdAndStatus(queueId, JobStatus.COMPLETED);
        long failed = jobRepository.countByQueueIdAndStatus(queueId, JobStatus.FAILED);
        long deadLetter = jobRepository.countByQueueIdAndStatus(queueId, JobStatus.DEAD_LETTER);
        long completedLastHour = jobRepository.countCompletedInQueueSince(queueId, oneHourAgo);
        long failedLastHour = jobRepository.countFailedInQueueSince(queueId, oneHourAgo);
        Double avgExecutionTime = executionRepository.avgDurationInQueueSince(queueId, oneHourAgo);

        double successRate = (completedLastHour + failedLastHour) > 0
                ? (double) completedLastHour / (completedLastHour + failedLastHour) * 100.0
                : 100.0;

        return MetricsDto.builder()
                .totalJobs(pending + running + completed + failed + deadLetter)
                .pendingJobs(pending)
                .runningJobs(running)
                .completedJobs(completed)
                .failedJobs(failed)
                .deadLetterJobs(deadLetter)
                .completedLastHour(completedLastHour)
                .failedLastHour(failedLastHour)
                .avgExecutionTimeMs(avgExecutionTime)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .build();
    }
}
