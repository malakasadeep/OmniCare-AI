package com.omnicare.platform.integration.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * A provider that answers without a network or an API key.
 *
 * <p>Lives in main rather than test sources on purpose. It is how a fresh clone
 * runs and how the integration tests stay deterministic and free, and the plan's
 * acceptance criterion — flip one config value and the model swaps — only means
 * something if this is selectable the same way any other provider is.
 *
 * <p>Deterministic: the same request always produces the same answer, so a test
 * can assert on the reply instead of merely on its existence.
 */
class FakeLlmProvider implements LlmProvider {

    static final String PREFIX = "[fake-llm]";

    private static final int CHUNK_SIZE = 12;
    private static final Duration CHUNK_DELAY = Duration.ofMillis(15);

    @Override
    public String name() {
        return "fake";
    }

    /**
     * Splits the very same answer {@link #chat} produces, so the two paths can
     * never drift. The small delay is what makes a manual {@code curl -N} show
     * text arriving in pieces rather than all at once.
     */
    @Override
    public Flux<String> streamChat(LlmRequest request) {
        String answer = chat(request).content();
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < answer.length(); start += CHUNK_SIZE) {
            chunks.add(answer.substring(start, Math.min(start + CHUNK_SIZE, answer.length())));
        }
        return Flux.fromIterable(chunks).delayElements(CHUNK_DELAY);
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        String lastUserMessage = request.messages().reversed().stream()
                .filter(message -> message.role() == LlmRole.USER)
                .map(LlmMessage::content)
                .findFirst()
                .orElse("");

        String content = "%s I received %d message(s). You said: %s".formatted(
                PREFIX, request.messages().size(), lastUserMessage);

        // Roughly four characters to a token — close enough to exercise the
        // budgeting code paths without pretending to be a real tokeniser.
        int promptTokens = request.messages().stream()
                .mapToInt(message -> message.content().length() / 4)
                .sum();

        return new LlmResponse(
                content,
                promptTokens,
                content.length() / 4,
                "stop");
    }
}
