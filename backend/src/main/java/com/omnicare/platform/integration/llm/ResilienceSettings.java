package com.omnicare.platform.integration.llm;

import java.time.Duration;

/**
 * How hard to try, and when to stop trying.
 *
 * @param maxAttempts          total attempts including the first, so 3 means one
 *                             try and two retries
 * @param initialBackoff       wait before the first retry; doubled each time
 * @param failureRateThreshold percentage of recent calls that must fail before
 *                             the circuit opens
 * @param slidingWindowSize    how many recent calls that percentage is measured over
 * @param minimumCalls         calls needed before the rate is judged at all,
 *                             so one unlucky failure at startup cannot open it
 * @param openStateWait        how long the circuit stays open before admitting
 *                             trial calls again
 */
public record ResilienceSettings(
        int maxAttempts,
        Duration initialBackoff,
        float failureRateThreshold,
        int slidingWindowSize,
        int minimumCalls,
        Duration openStateWait) {

    public ResilienceSettings {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, was " + maxAttempts);
        }
        // The breaker sits inside the retry, so it counts each attempt, not each
        // request. If it can reach its minimum within one request's worth of
        // attempts it will open part-way through that request — the third
        // attempt is refused, retry stops early, and the retry budget is
        // silently smaller than configured. Found by a test asserting three
        // attempts and getting two.
        if (minimumCalls <= maxAttempts) {
            throw new IllegalArgumentException(
                    ("minimumCalls (%d) must exceed maxAttempts (%d), or the circuit can open "
                            + "part-way through a single request and cut its retries short")
                            .formatted(minimumCalls, maxAttempts));
        }
        if (slidingWindowSize < minimumCalls) {
            throw new IllegalArgumentException(
                    "slidingWindowSize (%d) must be at least minimumCalls (%d)"
                            .formatted(slidingWindowSize, minimumCalls));
        }
    }
}
