package com.jobscheduler.websocket;

import com.jobscheduler.api.dto.MetricsDto;
import com.jobscheduler.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Publishes events to WebSocket topics for real-time dashboard updates.
 * 
 * Topics:
 * - /topic/metrics     — global metrics (every 5s)
 * - /topic/jobs        — job status changes
 * - /topic/workers     — worker status changes
 * - /topic/queues/{id} — queue-specific updates
 */
@Component
public class JobEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(JobEventPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final MetricsService metricsService;

    public JobEventPublisher(SimpMessagingTemplate messagingTemplate, MetricsService metricsService) {
        this.messagingTemplate = messagingTemplate;
        this.metricsService = metricsService;
    }

    /**
     * Broadcast global metrics every 5 seconds.
     */
    @Scheduled(fixedRate = 5000)
    public void broadcastMetrics() {
        try {
            MetricsDto metrics = metricsService.getGlobalMetrics();
            messagingTemplate.convertAndSend("/topic/metrics", metrics);
        } catch (Exception e) {
            log.debug("Failed to broadcast metrics: {}", e.getMessage());
        }
    }

    /**
     * Publish a job status change event.
     */
    public void publishJobUpdate(UUID jobId, String status, UUID queueId) {
        Map<String, Object> event = Map.of(
                "type", "JOB_STATUS_CHANGE",
                "jobId", jobId.toString(),
                "status", status,
                "queueId", queueId.toString(),
                "timestamp", System.currentTimeMillis()
        );
        messagingTemplate.convertAndSend("/topic/jobs", event);
        messagingTemplate.convertAndSend("/topic/queues/" + queueId, event);
    }

    /**
     * Publish a worker status change event.
     */
    public void publishWorkerUpdate(UUID workerId, String status, int activeJobs) {
        Map<String, Object> event = Map.of(
                "type", "WORKER_STATUS_CHANGE",
                "workerId", workerId.toString(),
                "status", status,
                "activeJobs", activeJobs,
                "timestamp", System.currentTimeMillis()
        );
        messagingTemplate.convertAndSend("/topic/workers", event);
    }
}
