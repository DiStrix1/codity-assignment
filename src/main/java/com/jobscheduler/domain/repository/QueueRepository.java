package com.jobscheduler.domain.repository;

import com.jobscheduler.domain.entity.Queue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface QueueRepository extends JpaRepository<Queue, UUID> {

    Page<Queue> findByProjectId(UUID projectId, Pageable pageable);

    /**
     * Fetch all active (non-paused) queues ordered by priority descending.
     * Used by the worker polling loop to determine which queues to claim from.
     */
    @Query("SELECT q FROM Queue q WHERE q.paused = false ORDER BY q.priority DESC")
    List<Queue> findActiveQueuesOrderedByPriority();

    boolean existsByProjectIdAndSlug(UUID projectId, String slug);
}
