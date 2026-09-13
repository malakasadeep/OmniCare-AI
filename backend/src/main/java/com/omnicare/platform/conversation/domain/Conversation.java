package com.omnicare.platform.conversation.domain;

import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.Guards;
import com.omnicare.platform.shared.domain.IllegalConversationStateException;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import com.omnicare.platform.shared.domain.VisitorId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Conversation {

    private final ConversationId id;
    private final TenantId tenantId;
    private final VisitorId visitorId;
    private final Instant createdAt;

    private ConversationStatus status;
    private Instant updatedAt;
    private UserId assignedOperator;
    private String escalationReason;

    private Conversation(ConversationId id,
                         TenantId tenantId,
                         VisitorId visitorId,
                         ConversationStatus status,
                         Instant createdAt,
                         Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.visitorId = Objects.requireNonNull(visitorId, "visitorId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static Conversation start(TenantId tenantId, VisitorId visitorId, Instant startedAt) {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        return new Conversation(
                ConversationId.generate(),
                tenantId,
                visitorId,
                ConversationStatus.BOT_ACTIVE,
                startedAt,
                startedAt);
    }

    /**
     * Rebuilds a conversation from state that was already persisted.
     *
     * <p>Unlike {@link #start}, this does not run the state machine — the stored
     * row is history, and history is not re-validated. It is meant for the
     * persistence mappers only; application code starts conversations and then
     * asks them to transition.
     */
    public static Conversation rehydrate(ConversationId id,
                                         TenantId tenantId,
                                         VisitorId visitorId,
                                         ConversationStatus status,
                                         UserId assignedOperator,
                                         String escalationReason,
                                         Instant createdAt,
                                         Instant updatedAt) {
        Conversation conversation =
                new Conversation(id, tenantId, visitorId, status, createdAt, updatedAt);
        conversation.assignedOperator = assignedOperator;
        conversation.escalationReason = escalationReason;
        return conversation;
    }

    public void escalateToHuman(String reason, Instant occurredAt) {
        String validReason = Guards.requireNonBlank(reason, "reason");
        transitionTo(ConversationStatus.AWAITING_HUMAN, occurredAt);
        this.escalationReason = validReason;
    }

    public void assignOperator(UserId operator, Instant occurredAt) {
        Objects.requireNonNull(operator, "operator must not be null");
        transitionTo(ConversationStatus.HUMAN_ACTIVE, occurredAt);
        this.assignedOperator = operator;
    }

    public void returnToBot(Instant occurredAt) {
        transitionTo(ConversationStatus.BOT_ACTIVE, occurredAt);
        this.assignedOperator = null;
    }

    public void resolve(Instant occurredAt) {
        transitionTo(ConversationStatus.RESOLVED, occurredAt);
    }

    /**
     * The single gate for every status change. Validates the move against
     * {@link ConversationStatus}'s table and only then mutates: a rejected
     * transition leaves this entity exactly as it was.
     */
    private void transitionTo(ConversationStatus target, Instant occurredAt) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (!status.canTransitionTo(target)) {
            throw new IllegalConversationStateException(id, status.name(), target.name());
        }
        this.status = target;
        this.updatedAt = occurredAt;
    }

    public ConversationId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public VisitorId visitorId() {
        return visitorId;
    }

    public ConversationStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Optional<UserId> assignedOperator() {
        return Optional.ofNullable(assignedOperator);
    }

    public Optional<String> escalationReason() {
        return Optional.ofNullable(escalationReason);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Conversation other)) {
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
        return "Conversation[id=%s, tenant=%s, status=%s]".formatted(
                id.value(), tenantId.value(), status);
    }
}
