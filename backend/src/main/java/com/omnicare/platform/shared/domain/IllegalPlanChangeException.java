package com.omnicare.platform.shared.domain;

import java.io.Serial;

/**
 * Raised when a {@code Tenant} is asked to change to a plan it is already on.
 *
 * <p>The plan is carried as its enum name to keep {@code shared} free of any
 * dependency on the {@code tenant} package that owns the {@code Plan} enum.
 */
public final class IllegalPlanChangeException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final TenantId tenantId;
    private final String plan;

    public IllegalPlanChangeException(TenantId tenantId, String plan) {
        super("Tenant %s is already on plan %s".formatted(tenantId.value(), plan));
        this.tenantId = tenantId;
        this.plan = plan;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public String getPlan() {
        return plan;
    }
}
