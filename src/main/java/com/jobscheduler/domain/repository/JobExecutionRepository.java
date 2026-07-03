package com.jobscheduler.domain.repository;

import com.jobscheduler.domain.entity.JobExecution;
import com.jobscheduler.domain.enums.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {

    List<JobExecution> findByJobIdOrderByAttemptNumberAsc(UUID jobId);

    Page<JobExecution> findByJobId(UUID jobId, Pageable pageable);

    @Query("SELECT AVG(je.durationMs) FROM JobExecution je WHERE je.status = 'COMPLETED' AND je.createdAt >= :since")
    Double avgDurationSince(@Param("since") Instant since);

    @Query("SELECT AVG(je.durationMs) FROM JobExecution je WHERE je.status = 'COMPLETED' AND je.job.queue.id = :queueId AND je.createdAt >= :since")
    Double avgDurationInQueueSince(@Param("queueId") UUID queueId, @Param("since") Instant since);

    @Query("SELECT COUNT(je) FROM JobExecution je WHERE je.status = :status AND je.createdAt >= :since")
    long countByStatusSince(@Param("status") ExecutionStatus status, @Param("since") Instant since);
}
