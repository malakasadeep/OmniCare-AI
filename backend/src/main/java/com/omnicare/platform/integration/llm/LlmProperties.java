package com.omnicare.platform.integration.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the {@code omnicare.llm.*} block.
 *
 * <p>The API key is bound from configuration, which resolves it from an
 * environment variable. It is never a literal in this repository — a key in git
 * is a key that has leaked, and rotating one out of history is far harder than
 * keeping it out.
 */
@ConfigurationProperties(prefix = "omnicare.llm")
public record LlmProperties(
        String provider,
        String model,
        double temperature,
        int maxTokens,
        /** How long to wait for a connection. Short: a provider that will not
         *  answer the handshake is not going to answer the request either. */
        Duration connectTimeout,
        /** How long to wait between reads. Never infinite — an unbounded wait
         *  holds a request thread until the socket happens to die. */
        Duration timeout,
        Resilience resilience,
        Groq groq) {

    public record Groq(String baseUrl, String apiKey) {
    }

    public record Resilience(
            int maxAttempts,
            Duration initialBackoff,
            float failureRateThreshold,
            int slidingWindowSize,
            int minimumCalls,
            Duration openStateWait) {

        ResilienceSettings toSettings() {
            return new ResilienceSettings(maxAttempts, initialBackoff, failureRateThreshold,
                    slidingWindowSize, minimumCalls, openStateWait);
        }
    }
}
