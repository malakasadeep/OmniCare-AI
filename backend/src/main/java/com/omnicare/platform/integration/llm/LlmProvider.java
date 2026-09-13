package com.omnicare.platform.integration.llm;

import reactor.core.publisher.Flux;

/**
 * A language model this platform can talk to — the Strategy.
 *
 * <p>Adding a provider means adding an implementation and a configuration value.
 * No existing class is edited, which is what the Open/Closed Principle buys in
 * practice: {@code LlmProviderFactory} discovers implementations rather than
 * listing them, so it does not change either.
 */
public interface LlmProvider {

    /**
     * The name this provider is selected by in configuration. Lower case, and
     * the one place a vendor's name is allowed to appear.
     */
    String name();

    /**
     * @throws LlmProviderException if the provider refused, failed, or returned
     *                              something unusable
     */
    LlmResponse chat(LlmRequest request);

    /**
     * The same completion, delivered as it is generated.
     *
     * <p>Each element is a fragment of the answer, not a whole one: concatenating
     * every element in order must give exactly what {@link #chat} would have
     * returned. Fragments are whatever the provider emits — usually sub-word
     * pieces — so a caller must never assume one element is one word.
     *
     * <p>Failures arrive as an error signal carrying {@link LlmProviderException}
     * rather than as a thrown exception, because by the time one happens the
     * method has long since returned.
     */
    Flux<String> streamChat(LlmRequest request);
}
