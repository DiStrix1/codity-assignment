package com.jobscheduler.domain.repository;

import com.jobscheduler.domain.entity.WorkerHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkerHeartbeatRepository extends JpaRepository<WorkerHeartbeat, Long> {
    List<WorkerHeartbeat> findTop10ByWorkerIdOrderByCreatedAtDesc(UUID workerId);
}
