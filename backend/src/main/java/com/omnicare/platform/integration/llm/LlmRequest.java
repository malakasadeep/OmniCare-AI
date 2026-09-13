package com.omnicare.platform.integration.llm;

import com.omnicare.platform.shared.domain.Guards;
import java.util.List;
import java.util.Objects;

/**
 * A chat completion request, in terms no vendor owns.
 *
 * <p>Nothing here names a provider, and nothing here is shaped by one's JSON.
 * That is the whole point of the abstraction: the day a second provider arrives,
 * or the first one is swapped out, this type does not change and neither does
 * any caller. Translating into a vendor's wire format is each
 * {@link LlmProvider}'s private business.
 */
public record LlmRequest(List<LlmMessage> messages, String model, double temperature, int maxTokens) {

    public LlmRequest {
        Objects.requireNonNull(messages, "messages must not be null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        messages = List.copyOf(messages);
        model = Guards.requireNonBlank(model, "model");
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0 and 2, was " + temperature);
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive, was " + maxTokens);
        }
    }
}
