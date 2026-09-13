package com.omnicare.platform.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record VisitorId(UUID value) {

    public VisitorId {
        Objects.requireNonNull(value, "VisitorId value must not be null");
    }

    public static VisitorId generate() {
        return new VisitorId(UUID.randomUUID());
    }
}
