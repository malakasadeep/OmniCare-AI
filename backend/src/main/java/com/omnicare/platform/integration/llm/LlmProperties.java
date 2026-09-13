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
        Duration timeout,
        Groq groq) {

    public record Groq(String baseUrl, String apiKey) {
    }
}
