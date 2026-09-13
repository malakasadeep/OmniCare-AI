package com.omnicare.platform.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record MessageId(UUID value) {

    public MessageId {
        Objects.requireNonNull(value, "MessageId value must not be null");
    }

    public static MessageId generate() {
        return new MessageId(UUID.randomUUID());
    }
}
