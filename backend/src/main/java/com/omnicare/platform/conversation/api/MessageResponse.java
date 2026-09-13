package com.omnicare.platform.conversation.api;

import com.omnicare.platform.conversation.domain.Message;
import java.time.Instant;
import java.util.UUID;

/** A message as the API exposes it. */
public record MessageResponse(UUID id, String role, String content, Instant createdAt) {

    static MessageResponse from(Message message) {
        return new MessageResponse(
                message.id().value(),
                message.role().name(),
                message.content(),
                message.createdAt());
    }
}
