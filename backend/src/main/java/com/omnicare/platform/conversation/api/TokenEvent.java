package com.omnicare.platform.conversation.api;

/**
 * One fragment of a streamed answer.
 *
 * <p>A JSON object rather than the raw text, because SSE would corrupt the raw
 * text: the format strips a single space after {@code data:}, so a fragment
 * beginning with a space — which is most of them, since models emit
 * {@code " world"} not {@code "world"} — would silently lose it and the answer
 * would arrive with its words run together. Quoting inside JSON puts the
 * whitespace out of the format's reach.
 */
public record TokenEvent(String token) {
}
