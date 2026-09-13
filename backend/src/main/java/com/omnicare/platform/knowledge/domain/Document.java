package com.omnicare.platform.knowledge.domain;

import com.omnicare.platform.shared.domain.DocumentId;
import com.omnicare.platform.shared.domain.Guards;
import com.omnicare.platform.shared.domain.IllegalDocumentStateException;
import com.omnicare.platform.shared.domain.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A file a tenant uploaded to its knowledge base, together with where the
 * indexing pipeline has got to with it.
 *
 * <p>The retry budget lives here rather than in the worker: the worker asks for
 * pending documents and reports outcomes, and this class alone decides whether a
 * failure means "try again" or "give up".
 */
public final class Document {

    /** Indexing attempts allowed before a document is parked in {@code FAILED}. */
    public static final int MAX_ATTEMPTS = 3;

    private final DocumentId id;
    private final TenantId tenantId;
    private final String filename;
    private final String contentType;
    private final long sizeBytes;
    private final String storagePath;
    private final Instant createdAt;

    private DocumentStatus status;
    private int attempts;
    private String failureReason;
    private Instant updatedAt;

    private Document(DocumentId id,
                     TenantId tenantId,
                     String filename,
                     String contentType,
                     long sizeBytes,
                     String storagePath,
                     DocumentStatus status,
                     Instant createdAt,
                     Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.filename = Objects.requireNonNull(filename, "filename must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.sizeBytes = sizeBytes;
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static Document upload(TenantId tenantId,
                                  String filename,
                                  String contentType,
                                  long sizeBytes,
                                  String storagePath,
                                  Instant uploadedAt) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
        return new Document(
                DocumentId.generate(),
                tenantId,
                Guards.requireNonBlank(filename, "filename"),
                Guards.requireNonBlank(contentType, "contentType"),
                Guards.requirePositive(sizeBytes, "sizeBytes"),
                Guards.requireNonBlank(storagePath, "storagePath"),
                DocumentStatus.PENDING,
                uploadedAt,
                uploadedAt);
    }

    /** Claims the document for a worker, spending one of its attempts. */
    public void startIndexing(Instant occurredAt) {
        transitionTo(DocumentStatus.INDEXING, occurredAt);
        this.attempts++;
    }

    public void markIndexed(Instant occurredAt) {
        transitionTo(DocumentStatus.INDEXED, occurredAt);
        this.failureReason = null;
    }

    /**
     * Reports that this attempt failed. Whether that means another try or the
     * end of the road is decided here, from {@link #attempts} against
     * {@link #MAX_ATTEMPTS} — the caller does not get a say.
     */
    public void recordFailure(String reason, Instant occurredAt) {
        String validReason = Guards.requireNonBlank(reason, "reason");
        transitionTo(hasAttemptsLeft() ? DocumentStatus.PENDING : DocumentStatus.FAILED, occurredAt);
        this.failureReason = validReason;
    }

    private boolean hasAttemptsLeft() {
        return attempts < MAX_ATTEMPTS;
    }

    /**
     * The single gate for every status change. Validates the move against
     * {@link DocumentStatus}'s table and only then mutates: a rejected
     * transition leaves this entity exactly as it was.
     */
    private void transitionTo(DocumentStatus target, Instant occurredAt) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (!status.canTransitionTo(target)) {
            throw new IllegalDocumentStateException(id, status.name(), target.name());
        }
        this.status = target;
        this.updatedAt = occurredAt;
    }

    public DocumentId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String filename() {
        return filename;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String storagePath() {
        return storagePath;
    }

    public DocumentStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
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
        if (!(o instanceof Document other)) {
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
        return "Document[id=%s, tenant=%s, filename=%s, status=%s, attempts=%d]".formatted(
                id.value(), tenantId.value(), filename, status, attempts);
    }
}
