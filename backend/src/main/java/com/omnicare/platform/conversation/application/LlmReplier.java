package com.omnicare.platform.conversation.application;

import com.omnicare.platform.integration.llm.LlmMessage;
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
 * <p>Conversation history and a token budget arrive on Day 9; today a request is
 * one system prompt and the visitor's latest message.
 */
@Component
class LlmReplier implements Replier {

    private static final String SYSTEM_PROMPT = """
            You are a customer support assistant for an online business.
            Answer in the same language the customer writes in.
            Be concise and friendly. If you do not know something, say so plainly
            rather than guessing.
            """;

    private final LlmProviderFactory providers;
    private final LlmProperties properties;

    LlmReplier(LlmProviderFactory providers, LlmProperties properties) {
        this.providers = providers;
        this.properties = properties;
    }

    @Override
    public String replyTo(String visitorMessage) {
        return providers.current().chat(requestFor(visitorMessage)).content();
    }

    @Override
    public Flux<String> streamReplyTo(String visitorMessage) {
        return providers.current().streamChat(requestFor(visitorMessage));
    }

    private LlmRequest requestFor(String visitorMessage) {
        return new LlmRequest(
                List.of(LlmMessage.system(SYSTEM_PROMPT), LlmMessage.user(visitorMessage)),
                properties.model(),
                properties.temperature(),
                properties.maxTokens());
    }
}
