package com.jobscheduler.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "worker_heartbeats")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class WorkerHeartbeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "active_jobs", nullable = false)
    private int activeJobs;

    @Column(name = "memory_used_mb")
    private Long memoryUsedMb;

    @Column(name = "cpu_load")
    private Double cpuLoad;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
