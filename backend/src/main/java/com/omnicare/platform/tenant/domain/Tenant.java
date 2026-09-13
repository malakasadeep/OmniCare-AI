package com.omnicare.platform.tenant.domain;

import com.omnicare.platform.shared.domain.Guards;
import com.omnicare.platform.shared.domain.IllegalPlanChangeException;
import com.omnicare.platform.shared.domain.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * A customer account. Everything else in the platform hangs off a tenant, and
 * every tenant-owned row carries its {@link TenantId}.
 */
public final class Tenant {

    private final TenantId id;
    private final Instant createdAt;

    private final String name;
    private Plan plan;
    private Instant updatedAt;

    private Tenant(TenantId id, String name, Plan plan, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static Tenant register(String name, Plan plan, Instant registeredAt) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(registeredAt, "registeredAt must not be null");
        return new Tenant(
                TenantId.generate(),
                Guards.requireNonBlank(name, "name"),
                plan,
                registeredAt,
                registeredAt);
    }

    /**
     * Moves the tenant onto a different plan. Asking for the plan it already
     * holds is rejected rather than silently ignored: it almost always means the
     * caller is working from stale state, and a no-op would hide that.
     */
    /**
     * Rebuilds a tenant from state that was already persisted. For the
     * persistence mappers only — see {@code Conversation.rehydrate}.
     */
    public static Tenant rehydrate(TenantId id, String name, Plan plan, Instant createdAt, Instant updatedAt) {
        return new Tenant(id, name, plan, createdAt, updatedAt);
    }

    public void changePlan(Plan newPlan, Instant occurredAt) {
        Objects.requireNonNull(newPlan, "newPlan must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (newPlan == plan) {
            throw new IllegalPlanChangeException(id, plan.name());
        }
        this.plan = newPlan;
        this.updatedAt = occurredAt;
    }

    public TenantId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Plan plan() {
        return plan;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tenant other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Tenant[id=%s, name=%s, plan=%s]".formatted(id.value(), name, plan);
    }
}
