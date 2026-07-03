package com.jobscheduler.domain.repository;

import com.jobscheduler.domain.entity.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, UUID> {

    /**
     * Finds scheduled jobs that are due to fire, with pessimistic locking
     * to prevent duplicate triggers across worker instances.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScheduledJob s WHERE s.enabled = true AND s.nextFireAt <= :now ORDER BY s.nextFireAt ASC")
    List<ScheduledJob> findDueScheduledJobsForUpdate(@Param("now") Instant now);

    List<ScheduledJob> findByQueueId(UUID queueId);

    List<ScheduledJob> findByEnabled(boolean enabled);
}
