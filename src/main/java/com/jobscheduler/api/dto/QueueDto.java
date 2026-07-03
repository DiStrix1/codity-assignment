package com.jobscheduler.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

public class QueueDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank(message = "Queue name is required")
        private String name;
        private String slug;
        private UUID projectId;
        private int priority;
        @Min(value = 1, message = "Max concurrency must be at least 1")
        private int maxConcurrency = 5;
        private UUID retryPolicyId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        private String name;
        private Integer priority;
        @Min(value = 1, message = "Max concurrency must be at least 1")
        private Integer maxConcurrency;
        private UUID retryPolicyId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID id;
        private String name;
        private String slug;
        private UUID projectId;
        private String projectName;
        private int priority;
        private int maxConcurrency;
        private UUID retryPolicyId;
        private String retryPolicyName;
        private boolean paused;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatsResponse {
        private UUID queueId;
        private String queueName;
        private long pending;
        private long running;
        private long completed;
        private long failed;
        private long deadLetter;
        private long scheduled;
        private boolean paused;
    }
}
