package com.omnicare.platform.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "TenantId value must not be null");
    }

    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }
}
