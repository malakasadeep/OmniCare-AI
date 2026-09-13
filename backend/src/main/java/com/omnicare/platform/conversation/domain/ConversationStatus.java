package com.omnicare.platform.conversation.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lifecycle of a {@code Conversation}, and the authority on which moves between
 * states are legal.
 *
 * <pre>
 *   BOT_ACTIVE      --escalate-->  AWAITING_HUMAN
 *   AWAITING_HUMAN  --assign---->  HUMAN_ACTIVE
 *   HUMAN_ACTIVE    --return---->  BOT_ACTIVE
 *   (any non-terminal) --resolve--> RESOLVED     (terminal)
 * </pre>
 */
public enum ConversationStatus {

    BOT_ACTIVE,
    AWAITING_HUMAN,
    HUMAN_ACTIVE,
    RESOLVED;

    /**
     * The complete transition table. A status is {@link #isTerminal() terminal}
     * exactly when its set of targets is empty, so this map is the single source
     * of truth for both questions.
     */
    private static final Map<ConversationStatus, Set<ConversationStatus>> ALLOWED_TARGETS = Map.of(
            BOT_ACTIVE, EnumSet.of(AWAITING_HUMAN, RESOLVED),
            AWAITING_HUMAN, EnumSet.of(HUMAN_ACTIVE, RESOLVED),
            HUMAN_ACTIVE, EnumSet.of(BOT_ACTIVE, RESOLVED),
            RESOLVED, EnumSet.noneOf(ConversationStatus.class));

    public boolean canTransitionTo(ConversationStatus target) {
        Objects.requireNonNull(target, "target status must not be null");
        return ALLOWED_TARGETS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED_TARGETS.get(this).isEmpty();
    }
}
