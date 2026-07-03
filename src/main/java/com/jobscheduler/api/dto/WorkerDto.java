package com.jobscheduler.api.dto;

import lombok.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class WorkerDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID id;
        private String hostname;
        private String status;
        private int maxConcurrentJobs;
        private int currentJobCount;
        private Instant registeredAt;
        private Instant lastHeartbeatAt;
    }
}
