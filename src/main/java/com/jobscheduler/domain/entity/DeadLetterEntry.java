package com.jobscheduler.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_queue")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DeadLetterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_job_id", nullable = false, unique = true)
    private Job originalJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @Column(name = "total_attempts", nullable = false)
    private int totalAttempts;

    @Column(name = "final_error", columnDefinition = "TEXT")
    private String finalError;

    @Column(name = "final_stack_trace", columnDefinition = "TEXT")
    private String finalStackTrace;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (failedAt == null) failedAt = now;
        if (createdAt == null) createdAt = now;
    }
}
