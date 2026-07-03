package com.jobscheduler.domain.repository;

import com.jobscheduler.domain.entity.Worker;
import com.jobscheduler.domain.enums.WorkerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    List<Worker> findByStatus(WorkerStatus status);

    @Query("SELECT w FROM Worker w WHERE w.status = 'ACTIVE' AND w.lastHeartbeatAt < :threshold")
    List<Worker> findStaleWorkers(@Param("threshold") Instant threshold);

    @Modifying
    @Query("UPDATE Worker w SET w.status = 'OFFLINE', w.currentJobCount = 0 WHERE w.status = 'ACTIVE' AND w.lastHeartbeatAt < :threshold")
    int markStaleWorkersOffline(@Param("threshold") Instant threshold);

    @Modifying
    @Query("UPDATE Worker w SET w.status = 'ACTIVE', w.lastHeartbeatAt = :now, w.currentJobCount = :jobCount WHERE w.id = :workerId")
    int updateHeartbeat(@Param("workerId") UUID workerId, @Param("now") Instant now, @Param("jobCount") int jobCount);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM Worker w WHERE w.status = 'OFFLINE'")
    int deleteOfflineWorkers();
}
