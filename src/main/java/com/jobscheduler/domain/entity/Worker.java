package com.jobscheduler.domain.entity;

import com.jobscheduler.domain.enums.WorkerStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workers")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Worker {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkerStatus status;

    @Column(name = "max_concurrent_jobs", nullable = false)
    private int maxConcurrentJobs;

    @Column(name = "current_job_count", nullable = false)
    private int currentJobCount;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (registeredAt == null) registeredAt = now;
        if (lastHeartbeatAt == null) lastHeartbeatAt = now;
        if (status == null) status = WorkerStatus.ACTIVE;
    }
}
