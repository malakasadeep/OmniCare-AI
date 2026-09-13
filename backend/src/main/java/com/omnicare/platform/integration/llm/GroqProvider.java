package com.omnicare.platform.integration.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
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

    /** The sentinel every OpenAI-compatible stream ends with. Not a token. */
    private static final String DONE = "[DONE]";

    private static final ObjectMapper STREAM_PARSER = new ObjectMapper();

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

    /**
     * Server-sent events from the provider, unwrapped into plain text fragments.
     *
     * <p>Three details of that format matter here. The stream is terminated by a
     * literal {@code [DONE]} payload, which is a sentinel and not a token — emit
     * it and it appears in the customer's answer. Many events carry a delta with
     * no content at all (the opening role announcement, the closing finish
     * reason), and those are skipped rather than sent on as empty events. And a
     * single unparseable event is dropped rather than allowed to fail the whole
     * stream, since by then the customer is already reading a half-written
     * answer.
     */
    @Override
    public Flux<String> streamChat(LlmRequest request) {
        return client.post()
                .uri(CHAT_COMPLETIONS)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(toWireFormat(request, true))
                .retrieve()
                .bodyToFlux(String.class)
                .takeUntil(DONE::equals)
                .filter(payload -> !DONE.equals(payload))
                .mapNotNull(GroqProvider::contentOf)
                .filter(content -> !content.isEmpty())
                .onErrorMap(e -> !(e instanceof LlmProviderException),
                        e -> new LlmProviderException("Language model stream failed", e));
    }

    /** @return the delta's text, or {@code null} for an event carrying none */
    private static String contentOf(String payload) {
        try {
            StreamChunk chunk = STREAM_PARSER.readValue(payload, StreamChunk.class);
            if (chunk.choices() == null || chunk.choices().isEmpty()) {
                return null;
            }
            Delta delta = chunk.choices().get(0).delta();
            return delta == null ? null : delta.content();
        } catch (JsonProcessingException e) {
            log.debug("Skipping unparseable stream event");
            return null;
        }
    }

    private static ChatCompletionRequest toWireFormat(LlmRequest request) {
        return toWireFormat(request, false);
    }

    private static ChatCompletionRequest toWireFormat(LlmRequest request, boolean stream) {
        List<WireMessage> messages = request.messages().stream()
                .map(m -> new WireMessage(m.role().name().toLowerCase(Locale.ROOT), m.content()))
                .toList();
        return new ChatCompletionRequest(
                request.model(), messages, request.temperature(), request.maxTokens(), stream);
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
    private record StreamChunk(List<StreamChoice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StreamChoice(Delta delta, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Delta(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens) {
    }
}
