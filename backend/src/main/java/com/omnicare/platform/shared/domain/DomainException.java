package com.omnicare.platform.shared.domain;

import java.io.Serial;

/**
 * Base type for every exception raised by a domain rule being broken
 * (an invalid state transition, a malformed value object, ...).
 *
 * <p>Unchecked, so the domain layer never forces {@code throws} clauses on
 * callers; the outer layers translate these into HTTP responses.
 */
public abstract class DomainException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected DomainException(String message) {
        super(message);
    }
}
