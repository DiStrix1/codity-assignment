package com.jobscheduler.domain.entity;

import com.jobscheduler.domain.enums.RetryStrategy;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retry_policies")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RetryPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RetryStrategy strategy;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "initial_delay_ms", nullable = false)
    private int initialDelayMs;

    @Column(nullable = false)
    private double multiplier;

    @Column(name = "max_delay_ms", nullable = false)
    private int maxDelayMs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (strategy == null) strategy = RetryStrategy.EXPONENTIAL;
        if (maxAttempts == 0) maxAttempts = 3;
        if (initialDelayMs == 0) initialDelayMs = 1000;
        if (multiplier == 0) multiplier = 2.0;
        if (maxDelayMs == 0) maxDelayMs = 300000;
    }
}
