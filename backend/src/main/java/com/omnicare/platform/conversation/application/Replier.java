package com.omnicare.platform.conversation.application;

import reactor.core.publisher.Flux;

/**
 * Produces the assistant's answer to a visitor's message.
 *
 * <p>An interface from the start even though today's only implementation is a
 * fixed string: it marks the seam where Day 7's {@code LlmProvider} plugs in,
 * so that change is a new implementation rather than surgery on
 * {@link ConversationService}.
 */
public interface Replier {

    String replyTo(String visitorMessage);

    /**
     * The same answer, delivered as it is produced. Concatenating every element
     * gives what {@link #replyTo} would have returned.
     */
    Flux<String> streamReplyTo(String visitorMessage);
}
