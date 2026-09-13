package com.omnicare.platform.shared.domain;

import java.io.Serial;

/**
 * Raised when a {@code Document} is asked to move to a status that its
 * state machine does not allow from the current one.
 *
 * <p>As with {@link IllegalConversationStateException}, the status values are
 * carried as enum names to keep {@code shared} free of any dependency on the
 * package that owns {@code DocumentStatus}.
 */
public final class IllegalDocumentStateException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DocumentId documentId;
    private final String currentStatus;
    private final String attemptedStatus;

    public IllegalDocumentStateException(DocumentId documentId,
                                         String currentStatus,
                                         String attemptedStatus) {
        super("Document %s cannot transition from %s to %s".formatted(
                documentId.value(), currentStatus, attemptedStatus));
        this.documentId = documentId;
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }

    public DocumentId getDocumentId() {
        return documentId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getAttemptedStatus() {
        return attemptedStatus;
    }
}
