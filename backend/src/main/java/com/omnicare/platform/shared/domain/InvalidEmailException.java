package com.omnicare.platform.shared.domain;

import java.io.Serial;

/**
 * Raised by {@link Email} when the supplied text is not a usable address
 * (blank, or missing an {@code "@"}). Carries the original text as given,
 * before normalisation, so callers can echo it back.
 */
public final class InvalidEmailException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String providedValue;

    public InvalidEmailException(String providedValue) {
        super("Not a valid email address: '%s'".formatted(providedValue));
        this.providedValue = providedValue;
    }

    public String getProvidedValue() {
        return providedValue;
    }
}
