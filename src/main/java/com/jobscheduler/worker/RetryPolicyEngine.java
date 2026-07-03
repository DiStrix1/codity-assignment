package com.jobscheduler.worker;

import com.jobscheduler.domain.enums.RetryStrategy;
import org.springframework.stereotype.Component;

/**
 * Calculates retry delays based on the configured strategy.
 * Supports FIXED, LINEAR, and EXPONENTIAL backoff.
 */
@Component
public class RetryPolicyEngine {

    /**
     * Calculate the delay before the next retry attempt.
     *
     * @param strategy      The backoff strategy to use
     * @param attemptNumber The current attempt number (1-based)
     * @param initialDelay  The base delay in milliseconds
     * @param multiplier    The multiplier for exponential backoff
     * @param maxDelay      The maximum delay cap in milliseconds
     * @return The delay in milliseconds before the next retry
     */
    public long calculateDelay(RetryStrategy strategy, int attemptNumber,
                               long initialDelay, double multiplier, long maxDelay) {
        if (attemptNumber <= 0) return initialDelay;

        long delay = switch (strategy) {
            case FIXED -> initialDelay;
            case LINEAR -> initialDelay * attemptNumber;
            case EXPONENTIAL -> (long) (initialDelay * Math.pow(multiplier, attemptNumber - 1));
        };

        return Math.min(delay, maxDelay);
    }
}
