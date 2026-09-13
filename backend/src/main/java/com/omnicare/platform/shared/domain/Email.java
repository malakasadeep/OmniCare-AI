package com.omnicare.platform.shared.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * An email address, stored in a canonical form: surrounding whitespace stripped
 * and the whole string lower-cased, so {@code "Alice@Example.com"} and
 * {@code "  alice@example.com "} are the same value.
 *
 * <p>Validation is deliberately shallow — non-blank and contains an {@code "@"}.
 * Anything stricter belongs at the edges (a registration form), not in the
 * domain value object.
 */
public record Email(String value) {

    public Email {
        Objects.requireNonNull(value, "Email value must not be null");
        String normalised = value.strip().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty() || !normalised.contains("@")) {
            throw new InvalidEmailException(value);
        }
        value = normalised;
    }
}
