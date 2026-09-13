package com.omnicare.platform.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record DocumentId(UUID value) {

    public DocumentId {
        Objects.requireNonNull(value, "DocumentId value must not be null");
    }

    public static DocumentId generate() {
        return new DocumentId(UUID.randomUUID());
    }
}
