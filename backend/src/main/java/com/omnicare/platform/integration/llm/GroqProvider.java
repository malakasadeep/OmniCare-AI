package com.omnicare.platform.integration.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Groq, behind the neutral {@link LlmProvider} interface.
 *
 * <p>Everything vendor-shaped is confined to this file: the URL path, the
 * lower-case role strings, {@code max_tokens}'s snake case, the
 * {@code choices[0].message.content} burrowing. Callers see only
 * {@link LlmRequest} and {@link LlmResponse}.
 */
class GroqProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GroqProvider.class);

    private static final String CHAT_COMPLETIONS = "/chat/completions";

    private final WebClient client;
    private final Duration timeout;

    GroqProvider(WebClient client, Duration timeout) {
        this.client = client;
        this.timeout = timeout;
    }

    @Override
    public String name() {
        return "groq";
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        ChatCompletionResponse response;
        try {
            response = client.post()
                    .uri(CHAT_COMPLETIONS)
                    .bodyValue(toWireFormat(request))
                    .retrieve()
                    .bodyToMono(ChatCompletionResponse.class)
                    .block(timeout);
        } catch (WebClientResponseException e) {
            // The status is safe to log and to act on. The body is not repeated
            // to the caller: providers quote the offending API key back in it.
            log.warn("Groq refused the request with {}", e.getStatusCode());
            throw new LlmProviderException(
                    "Language model request failed with status " + e.getStatusCode().value(), e);
        } catch (RuntimeException e) {
            log.warn("Groq request failed: {}", e.toString());
            throw new LlmProviderException("Language model request failed", e);
        }

        return toNeutralFormat(response);
    }

    private static ChatCompletionRequest toWireFormat(LlmRequest request) {
        List<WireMessage> messages = request.messages().stream()
                .map(m -> new WireMessage(m.role().name().toLowerCase(Locale.ROOT), m.content()))
                .toList();
        return new ChatCompletionRequest(
                request.model(), messages, request.temperature(), request.maxTokens(), false);
    }

    private static LlmResponse toNeutralFormat(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmProviderException("Language model returned no choices");
        }
        Choice choice = response.choices().get(0);
        if (choice.message() == null || choice.message().content() == null
                || choice.message().content().isBlank()) {
            throw new LlmProviderException("Language model returned an empty message");
        }
        Usage usage = response.usage() == null ? new Usage(0, 0) : response.usage();
        return new LlmResponse(
                choice.message().content(),
                usage.promptTokens(),
                usage.completionTokens(),
                choice.finishReason());
    }

    // --- Wire format. Package-private records, never exposed beyond this class. ---

    private record ChatCompletionRequest(
            String model,
            List<WireMessage> messages,
            double temperature,
            @JsonProperty("max_tokens") int maxTokens,
            boolean stream) {
    }

    private record WireMessage(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(WireMessage message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens) {
    }
}
