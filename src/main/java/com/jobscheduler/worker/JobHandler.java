package com.jobscheduler.worker;

import com.jobscheduler.domain.entity.Job;
import java.util.Map;

/**
 * Interface for pluggable job execution handlers.
 * Implementations define what happens when a job is actually "executed".
 */
public interface JobHandler {

    /**
     * Execute the job's payload.
     * @param job The job being executed
     * @return Result of the execution
     * @throws Exception if execution fails
     */
    JobResult execute(Job job) throws Exception;

    /**
     * Whether this handler can handle jobs with the given payload.
     */
    default boolean canHandle(Map<String, Object> payload) {
        return true;
    }

    record JobResult(boolean success, String message, Map<String, Object> outputMetadata) {
        public static JobResult success(String message) {
            return new JobResult(true, message, Map.of());
        }
        public static JobResult failure(String message) {
            return new JobResult(false, message, Map.of());
        }
    }
}
