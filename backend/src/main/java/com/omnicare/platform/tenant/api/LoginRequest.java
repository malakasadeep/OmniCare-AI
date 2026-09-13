package com.omnicare.platform.tenant.api;

import jakarta.validation.constraints.NotBlank;

/**
 * The login payload.
 *
 * <p>Deliberately not bean-validated beyond "present": rejecting a malformed
 * address with a 400 here would confirm that a well-formed one is at least
 * plausible. Anything wrong is one indistinguishable 401.
 */
public record LoginRequest(

        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "password is required")
        String password) {
}
