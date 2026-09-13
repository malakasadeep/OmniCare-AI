package com.omnicare.platform.knowledge.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lifecycle of a {@code Document} as it moves through the indexing pipeline,
 * and the authority on which moves between states are legal.
 *
 * <pre>
 *   PENDING   --picked up by worker-->  INDEXING
 *   INDEXING  --success------------->   INDEXED   (terminal)
 *   INDEXING  --failure, retries left-> PENDING
 *   INDEXING  --failure, none left--->  FAILED    (terminal)
 * </pre>
 *
 * <p>The retry arrow back to {@code PENDING} is what lets a single worker query
 * ({@code WHERE status = 'PENDING'}) pick a document up again without a second
 * queue.
 */
public enum DocumentStatus {

    PENDING,
    INDEXING,
    INDEXED,
    FAILED;

    /**
     * The complete transition table: the single source of truth for which
     * moves are legal. A status with an empty set of targets is terminal.
     */
    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED_TARGETS = Map.of(
            PENDING, EnumSet.of(INDEXING),
            INDEXING, EnumSet.of(INDEXED, PENDING, FAILED),
            INDEXED, EnumSet.noneOf(DocumentStatus.class),
            FAILED, EnumSet.noneOf(DocumentStatus.class));

    public boolean canTransitionTo(DocumentStatus target) {
        Objects.requireNonNull(target, "target status must not be null");
        return ALLOWED_TARGETS.get(this).contains(target);
    }
}
