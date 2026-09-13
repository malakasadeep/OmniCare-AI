package com.omnicare.platform.integration.llm;

import java.io.Serial;

/**
 * A provider call did not produce a usable answer.
 *
 * <p>One type for every cause — refused, rate limited, timed out, malformed
 * response — because callers respond to all of them the same way, and the
 * circuit breaker needs a single thing to count.
 *
 * <p>{@link #isRetryable()} is the one distinction that matters. A rate limit or
 * a 502 will likely succeed on the next attempt; a malformed request or a
 * rejected key will fail identically every time, and retrying it only spends the
 * visitor's patience and, on a metered API, their money.
 */
public class LlmProviderException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean retryable;

    public LlmProviderException(String message) {
        this(message, null, false);
    }

    public LlmProviderException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public LlmProviderException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** Whether trying the same call again could plausibly succeed. */
    public boolean isRetryable() {
        return retryable;
    }
}
