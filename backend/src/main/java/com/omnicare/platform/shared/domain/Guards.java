package com.omnicare.platform.shared.domain;

import java.util.Objects;

/**
 * Argument checks shared by the domain entities, so each one does not carry its
 * own copy.
 *
 * <p>The split between {@link NullPointerException} and
 * {@link IllegalArgumentException} is deliberate and consistent across the
 * domain: {@code null} is a programming error, a blank or out-of-range value is
 * bad input.
 */
public final class Guards {

    private Guards() {
    }

    /**
     * @return {@code value} with surrounding whitespace stripped, so callers
     *         store the canonical form rather than whatever the edge sent
     */
    public static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return stripped;
    }

    public static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive, was " + value);
        }
        return value;
    }
}
