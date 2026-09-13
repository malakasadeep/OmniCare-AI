package com.omnicare.platform.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record ConversationId(UUID value) {

    public ConversationId {
        Objects.requireNonNull(value, "ConversationId value must not be null");
    }

    public static ConversationId generate() {
        return new ConversationId(UUID.randomUUID());
    }
}
