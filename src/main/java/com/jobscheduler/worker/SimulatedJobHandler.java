package com.jobscheduler.worker;

import com.jobscheduler.domain.entity.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated job handler for demonstration and testing.
 * 
 * Simulates job execution with configurable:
 * - Duration: random between 500ms-3000ms (or payload-specified)
 * - Failure rate: 10% by default (or payload-specified)
 * 
 * Payload options:
 *   "duration_ms": 2000         // execution duration
 *   "fail": true                // force failure
 *   "fail_rate": 0.2            // 20% failure rate
 *   "error_message": "custom"   // custom error message on failure
 */
@Component
public class SimulatedJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(SimulatedJobHandler.class);
    private static final double DEFAULT_FAIL_RATE = 0.10;

    @Override
    public JobResult execute(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload();

        // Determine execution duration
        long durationMs = payload.containsKey("duration_ms")
                ? ((Number) payload.get("duration_ms")).longValue()
                : ThreadLocalRandom.current().nextLong(500, 3000);

        log.debug("Simulating job {} execution for {}ms", job.getId(), durationMs);
        Thread.sleep(durationMs);

        // Determine if this execution should fail
        boolean forceFail = Boolean.TRUE.equals(payload.get("fail"));
        double failRate = payload.containsKey("fail_rate")
                ? ((Number) payload.get("fail_rate")).doubleValue()
                : DEFAULT_FAIL_RATE;

        boolean shouldFail = forceFail || ThreadLocalRandom.current().nextDouble() < failRate;

        if (shouldFail) {
            String errorMsg = payload.containsKey("error_message")
                    ? (String) payload.get("error_message")
                    : "Simulated job failure (attempt " + job.getAttemptCount() + ")";
            throw new RuntimeException(errorMsg);
        }

        return JobResult.success("Job completed successfully after " + durationMs + "ms");
    }
}
