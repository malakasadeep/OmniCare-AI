package com.omnicare.platform.conversation.application;

import com.omnicare.platform.conversation.domain.Message;
import com.omnicare.platform.conversation.prompt.PromptBuilder;
import com.omnicare.platform.integration.llm.LlmProperties;
import com.omnicare.platform.integration.llm.LlmProviderFactory;
import com.omnicare.platform.integration.llm.LlmRequest;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Answers a visitor by asking the configured language model.
 *
 * <p>Fills the seam {@link Replier} opened on Day 6. Note what it does not do:
 * it names no vendor, chooses no provider and knows no URL — it asks
 * {@link LlmProviderFactory} for whichever provider configuration selected.
 * Switching models is a configuration change, and this class never learns of it.
 *
 * <p>The prompt itself — system instructions, how much history fits, and the
 * token budget — is {@link PromptBuilder}'s business, not this class's.
 */
@Component
class LlmReplier implements Replier {

    private final LlmProviderFactory providers;
    private final LlmProperties properties;
    private final PromptBuilder promptBuilder;

    LlmReplier(LlmProviderFactory providers, LlmProperties properties, PromptBuilder promptBuilder) {
        this.providers = providers;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public String replyTo(List<Message> history, String visitorMessage) {
        return providers.current().chat(requestFor(history, visitorMessage)).content();
    }

    @Override
    public Flux<String> streamReplyTo(List<Message> history, String visitorMessage) {
        return providers.current().streamChat(requestFor(history, visitorMessage));
    }

    private LlmRequest requestFor(List<Message> history, String visitorMessage) {
        return new LlmRequest(
                promptBuilder.build(history, visitorMessage),
                properties.model(),
                properties.temperature(),
                properties.maxTokens());
    }
}
