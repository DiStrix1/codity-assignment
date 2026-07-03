package com.jobscheduler.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JobDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotNull(message = "Queue ID is required")
        private UUID queueId;

        private String type; // IMMEDIATE, DELAYED, SCHEDULED, RECURRING, BATCH

        @NotNull(message = "Payload is required")
        private Map<String, Object> payload;

        private String idempotencyKey;
        private UUID batchId;
        private int priority;
        private Instant scheduledAt; // For DELAYED/SCHEDULED types
        private String cronExpression; // For RECURRING type
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BatchCreateRequest {
        @NotNull(message = "Queue ID is required")
        private UUID queueId;

        @NotNull(message = "Jobs list is required")
        private List<BatchJobItem> jobs;

        private int priority;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BatchJobItem {
        @NotNull(message = "Payload is required")
        private Map<String, Object> payload;
        private String idempotencyKey;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID id;
        private UUID queueId;
        private String queueName;
        private String type;
        private String status;
        private Map<String, Object> payload;
        private String idempotencyKey;
        private UUID batchId;
        private int priority;
        private int attemptCount;
        private int maxAttempts;
        private Instant scheduledAt;
        private Instant claimedAt;
        private UUID claimedByWorker;
        private Instant startedAt;
        private Instant completedAt;
        private String errorMessage;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DetailResponse {
        private Response job;
        private List<ExecutionResponse> executions;
        private List<LogResponse> recentLogs;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ExecutionResponse {
        private UUID id;
        private int attemptNumber;
        private UUID workerId;
        private String status;
        private Instant startedAt;
        private Instant endedAt;
        private Long durationMs;
        private String errorMessage;
        private String stackTrace;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LogResponse {
        private Long id;
        private String level;
        private String message;
        private Map<String, Object> metadata;
        private Instant createdAt;
    }
}
