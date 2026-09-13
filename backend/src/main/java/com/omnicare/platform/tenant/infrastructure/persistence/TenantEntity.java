package com.omnicare.platform.tenant.infrastructure.persistence;

import com.omnicare.platform.tenant.domain.Plan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code tenants} row.
 *
 * <p>A separate class from {@code Tenant} on purpose: this one is an anemic bag
 * of columns shaped by Hibernate's requirements (no-arg constructor, mutable
 * fields), and keeping it out of the domain is what lets the domain have no
 * setters at all.
 */
@Entity
@Table(name = "tenants")
class TenantEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantEntity() {
    }

    TenantEntity(UUID id, String name, Plan plan, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.plan = plan;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    String getName() {
        return name;
    }

    Plan getPlan() {
        return plan;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
