package com.jobscheduler.domain.repository;

import com.jobscheduler.domain.entity.JobLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface JobLogRepository extends JpaRepository<JobLog, Long> {
    Page<JobLog> findByJobIdOrderByCreatedAtAsc(UUID jobId, Pageable pageable);
    Page<JobLog> findByExecutionIdOrderByCreatedAtAsc(UUID executionId, Pageable pageable);
}
