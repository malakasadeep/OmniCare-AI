package com.omnicare.platform.integration.llm;

import com.omnicare.platform.shared.domain.Guards;

/**
 * A chat completion result, in terms no vendor owns.
 *
 * <p>{@code finishReason} is kept as the provider's own string rather than
 * mapped to an enum. Providers disagree on the vocabulary and add values without
 * warning, and an unknown value should not be an exception — so the one question
 * the caller actually asks is answered by {@link #wasTruncated()}.
 */
public record LlmResponse(String content, int promptTokens, int completionTokens, String finishReason) {

    /** The value every OpenAI-compatible API uses when it hit the token budget. */
    private static final String TRUNCATED = "length";

    public LlmResponse {
        content = Guards.requireNonBlank(content, "content");
        if (promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
    }

    /** The answer stopped because it ran out of budget, not because it was finished. */
    public boolean wasTruncated() {
        return TRUNCATED.equalsIgnoreCase(finishReason);
    }

    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
