package com.omnicare.platform.integration.llm;

import java.io.Serial;

/**
 * A provider call did not produce a usable answer.
 *
 * <p>One type for every cause — refused, rate limited, timed out, malformed
 * response — because callers respond to all of them the same way, and Day 10's
 * circuit breaker needs a single thing to count.
 */
public class LlmProviderException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LlmProviderException(String message) {
        super(message);
    }

    public LlmProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
