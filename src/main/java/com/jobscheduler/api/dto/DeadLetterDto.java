package com.jobscheduler.api.dto;

import lombok.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class DeadLetterDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID id;
        private UUID originalJobId;
        private UUID queueId;
        private String queueName;
        private Map<String, Object> payload;
        private int totalAttempts;
        private String finalError;
        private String finalStackTrace;
        private Instant failedAt;
        private Instant createdAt;
    }
}
