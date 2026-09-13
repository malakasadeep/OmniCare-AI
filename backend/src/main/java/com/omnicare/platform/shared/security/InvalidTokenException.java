package com.omnicare.platform.shared.security;

import java.io.Serial;

/**
 * Raised for every way a token can fail to be usable — bad signature, expired,
 * malformed, or the wrong kind of token for where it was presented.
 *
 * <p>Deliberately one type with a coarse message: telling a caller whether a
 * token was expired versus forged is free information for an attacker.
 */
public class InvalidTokenException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidTokenException(String message) {
        super(message);
    }
}
