package com.omnicare.platform.integration.llm;

import com.omnicare.platform.shared.domain.Guards;
import java.util.Objects;

/** One turn as sent to a model. */
public record LlmMessage(LlmRole role, String content) {

    public LlmMessage {
        Objects.requireNonNull(role, "role must not be null");
        content = Guards.requireNonBlank(content, "content");
    }

    public static LlmMessage system(String content) {
        return new LlmMessage(LlmRole.SYSTEM, content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(LlmRole.USER, content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(LlmRole.ASSISTANT, content);
    }
}
