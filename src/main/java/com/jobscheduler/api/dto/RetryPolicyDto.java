package com.jobscheduler.api.dto;

import com.jobscheduler.domain.enums.RetryStrategy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

public class RetryPolicyDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        @NotBlank(message = "Policy name is required")
        private String name;

        @NotNull(message = "Retry strategy is required")
        private RetryStrategy strategy;

        @Min(value = 1, message = "Max attempts must be at least 1")
        private int maxAttempts = 3;

        @Min(value = 0, message = "Initial delay must be non-negative")
        private int initialDelayMs = 1000;

        @Min(value = 1, message = "Multiplier must be at least 1.0")
        private double multiplier = 2.0;

        @Min(value = 0, message = "Max delay must be non-negative")
        private int maxDelayMs = 300000;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID id;
        private String name;
        private RetryStrategy strategy;
        private int maxAttempts;
        private int initialDelayMs;
        private double multiplier;
        private int maxDelayMs;
        private Instant createdAt;
    }
}
