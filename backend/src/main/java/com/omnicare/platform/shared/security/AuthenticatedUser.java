package com.omnicare.platform.shared.security;

import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import java.util.Objects;

/**
 * Who the current request is acting as, as carried in a JWT.
 *
 * <p>The role is a plain string rather than the {@code UserRole} enum: this
 * package is generic security plumbing and must not depend on the tenant
 * feature package, and Spring's authorities are strings anyway.
 */
public record AuthenticatedUser(UserId userId, TenantId tenantId, String role) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }
}
