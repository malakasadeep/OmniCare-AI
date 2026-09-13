package com.omnicare.platform.shared.api;

import java.io.Serial;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Base type for failures that a feature wants turned into a specific HTTP
 * response.
 *
 * <p>It exists to keep the dependency pointing the right way. Without it,
 * {@code ApiExceptionHandler} would have to import an exception type from every
 * feature package, making {@code shared} depend on {@code tenant},
 * {@code conversation} and the rest — the exact inversion a modular monolith is
 * supposed to prevent. Instead each feature depends on this, and the handler
 * only ever sees {@code ApplicationException}.
 *
 * <p>The message is the response body's {@code detail}, so subclasses must write
 * messages that are safe to show a caller.
 */
public abstract class ApplicationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient HttpStatus status;

    protected ApplicationException(HttpStatus status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public HttpStatus status() {
        return status;
    }
}
