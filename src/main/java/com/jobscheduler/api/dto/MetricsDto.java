package com.jobscheduler.api.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MetricsDto {

    private long totalJobs;
    private long pendingJobs;
    private long runningJobs;
    private long completedJobs;
    private long failedJobs;
    private long deadLetterJobs;
    private long completedLastHour;
    private long failedLastHour;
    private Double avgExecutionTimeMs;
    private double successRate;
    private int activeWorkers;
}
