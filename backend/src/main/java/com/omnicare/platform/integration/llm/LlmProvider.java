package com.omnicare.platform.integration.llm;

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
}
