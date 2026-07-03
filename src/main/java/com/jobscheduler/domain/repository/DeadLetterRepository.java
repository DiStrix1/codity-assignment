package com.jobscheduler.domain.repository;

import com.jobscheduler.domain.entity.DeadLetterEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntry, UUID> {
    Page<DeadLetterEntry> findByQueueIdOrderByCreatedAtDesc(UUID queueId, Pageable pageable);
    Page<DeadLetterEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Optional<DeadLetterEntry> findByOriginalJobId(UUID originalJobId);
    long countByQueueId(UUID queueId);
}
