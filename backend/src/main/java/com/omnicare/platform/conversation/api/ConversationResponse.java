package com.omnicare.platform.conversation.api;

import com.omnicare.platform.conversation.domain.Conversation;
import java.time.Instant;
import java.util.UUID;

/** A conversation as the API exposes it. */
public record ConversationResponse(UUID id,
                                   UUID visitorId,
                                   String status,
                                   Instant createdAt,
                                   Instant updatedAt) {

    static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.id().value(),
                conversation.visitorId().value(),
                conversation.status().name(),
                conversation.createdAt(),
                conversation.updatedAt());
    }
}
