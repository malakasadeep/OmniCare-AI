package com.omnicare.platform.shared.domain;

import java.io.Serial;

/**
 * Raised when a {@code Conversation} is asked to move to a status that its
 * state machine does not allow from the current one.
 *
 * <p>The status values are carried as their enum names rather than the
 * {@code ConversationStatus} type: that enum lives in the {@code conversation}
 * package, and {@code shared} must not depend on a feature package (doing so
 * would form a package cycle once {@code Conversation} throws this).
 */
public final class IllegalConversationStateException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ConversationId conversationId;
    private final String currentStatus;
    private final String attemptedStatus;

    public IllegalConversationStateException(ConversationId conversationId,
                                             String currentStatus,
                                             String attemptedStatus) {
        super("Conversation %s cannot transition from %s to %s".formatted(
                conversationId.value(), currentStatus, attemptedStatus));
        this.conversationId = conversationId;
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }

    public ConversationId getConversationId() {
        return conversationId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getAttemptedStatus() {
        return attemptedStatus;
    }
}
