package com.omnicare.platform.conversation.infrastructure.persistence;

import com.omnicare.platform.conversation.domain.ConversationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code conversations} row.
 *
 * <p>Related rows are referenced by raw id rather than with {@code @ManyToOne}
 * associations. Object graphs that lazily walk into other aggregates are how
 * N+1 queries and accidental cross-tenant reads start; a repository per
 * aggregate keeps each load explicit.
 */
@Entity
@Table(name = "conversations")
class ConversationEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "visitor_id", nullable = false)
    private UUID visitorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationStatus status;

    @Column(name = "assigned_operator_id")
    private UUID assignedOperatorId;

    @Column(name = "escalation_reason")
    private String escalationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationEntity() {
    }

    ConversationEntity(UUID id, UUID tenantId, UUID visitorId, ConversationStatus status,
                       UUID assignedOperatorId, String escalationReason,
                       Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.visitorId = visitorId;
        this.status = status;
        this.assignedOperatorId = assignedOperatorId;
        this.escalationReason = escalationReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getVisitorId() {
        return visitorId;
    }

    ConversationStatus getStatus() {
        return status;
    }

    UUID getAssignedOperatorId() {
        return assignedOperatorId;
    }

    String getEscalationReason() {
        return escalationReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
