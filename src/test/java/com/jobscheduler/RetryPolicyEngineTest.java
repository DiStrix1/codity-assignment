package com.jobscheduler;

import com.jobscheduler.domain.enums.RetryStrategy;
import com.jobscheduler.worker.RetryPolicyEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyEngineTest {

    private final RetryPolicyEngine engine = new RetryPolicyEngine();

    @Test
    @DisplayName("FIXED strategy returns constant delay")
    void fixedStrategy() {
        assertEquals(5000, engine.calculateDelay(RetryStrategy.FIXED, 1, 5000, 2.0, 300000));
        assertEquals(5000, engine.calculateDelay(RetryStrategy.FIXED, 2, 5000, 2.0, 300000));
        assertEquals(5000, engine.calculateDelay(RetryStrategy.FIXED, 10, 5000, 2.0, 300000));
    }

    @Test
    @DisplayName("LINEAR strategy scales linearly with attempt number")
    void linearStrategy() {
        assertEquals(1000, engine.calculateDelay(RetryStrategy.LINEAR, 1, 1000, 1.0, 300000));
        assertEquals(2000, engine.calculateDelay(RetryStrategy.LINEAR, 2, 1000, 1.0, 300000));
        assertEquals(3000, engine.calculateDelay(RetryStrategy.LINEAR, 3, 1000, 1.0, 300000));
        assertEquals(10000, engine.calculateDelay(RetryStrategy.LINEAR, 10, 1000, 1.0, 300000));
    }

    @Test
    @DisplayName("EXPONENTIAL strategy doubles delay each attempt")
    void exponentialStrategy() {
        assertEquals(1000, engine.calculateDelay(RetryStrategy.EXPONENTIAL, 1, 1000, 2.0, 300000));
        assertEquals(2000, engine.calculateDelay(RetryStrategy.EXPONENTIAL, 2, 1000, 2.0, 300000));
        assertEquals(4000, engine.calculateDelay(RetryStrategy.EXPONENTIAL, 3, 1000, 2.0, 300000));
        assertEquals(8000, engine.calculateDelay(RetryStrategy.EXPONENTIAL, 4, 1000, 2.0, 300000));
    }

    @Test
    @DisplayName("Delay is capped at maxDelay")
    void maxDelayCap() {
        // Exponential with 2x multiplier, attempt 20 → would be 1000 * 2^19 = 524,288,000
        // But maxDelay is 60000
        long delay = engine.calculateDelay(RetryStrategy.EXPONENTIAL, 20, 1000, 2.0, 60000);
        assertEquals(60000, delay, "Delay should be capped at maxDelay");

        // Linear with attempt 100 → 1000 * 100 = 100000, cap at 30000
        delay = engine.calculateDelay(RetryStrategy.LINEAR, 100, 1000, 1.0, 30000);
        assertEquals(30000, delay, "Linear delay should be capped at maxDelay");
    }

    @Test
    @DisplayName("Attempt 0 or negative returns initialDelay")
    void edgeCaseAttemptZero() {
        assertEquals(1000, engine.calculateDelay(RetryStrategy.EXPONENTIAL, 0, 1000, 2.0, 300000));
        assertEquals(1000, engine.calculateDelay(RetryStrategy.EXPONENTIAL, -1, 1000, 2.0, 300000));
    }

    @Test
    @DisplayName("Multiplier of 1.0 makes exponential behave like fixed")
    void exponentialWithMultiplierOne() {
        assertEquals(1000, engine.calculateDelay(RetryStrategy.EXPONENTIAL, 1, 1000, 1.0, 300000));
        assertEquals(1000, engine.calculateDelay(RetryStrategy.EXPONENTIAL, 5, 1000, 1.0, 300000));
        assertEquals(1000, engine.calculateDelay(RetryStrategy.EXPONENTIAL, 10, 1000, 1.0, 300000));
    }

    @ParameterizedTest
    @CsvSource({
        "EXPONENTIAL, 1, 500, 1.5, 60000, 500",
        "EXPONENTIAL, 2, 500, 1.5, 60000, 750",
        "EXPONENTIAL, 3, 500, 1.5, 60000, 1125",
        "EXPONENTIAL, 4, 500, 1.5, 60000, 1687",
    })
    @DisplayName("Exponential with 1.5x multiplier (aggressive retry preset)")
    void aggressiveRetryPreset(RetryStrategy strategy, int attempt, long initial,
                                double multiplier, long maxDelay, long expected) {
        long actual = engine.calculateDelay(strategy, attempt, initial, multiplier, maxDelay);
        assertEquals(expected, actual, "Attempt " + attempt + " should have delay " + expected);
    }
}
