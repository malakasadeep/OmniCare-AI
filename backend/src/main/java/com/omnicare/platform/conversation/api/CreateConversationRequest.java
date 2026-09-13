package com.omnicare.platform.conversation.api;

import java.util.UUID;

/**
 * Starting a conversation.
 *
 * <p>{@code visitorId} is optional: a widget that already knows its visitor
 * passes one so several conversations can be tied together, and a caller that
 * does not gets a fresh one.
 */
public record CreateConversationRequest(UUID visitorId) {
}
