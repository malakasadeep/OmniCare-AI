package com.omnicare.platform.tenant.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The registration payload.
 *
 * <p>A DTO rather than a domain object: this shape is part of the HTTP contract
 * and is allowed to change with the API, while the domain changes only when the
 * business rules do.
 */
public record RegisterRequest(

        @NotBlank(message = "companyName is required")
        @Size(max = 200, message = "companyName must be at most 200 characters")
        String companyName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email,

        // Length, not composition rules: a long passphrase beats a short one
        // with a symbol in it, and composition rules push people to reuse.
        @NotBlank(message = "password is required")
        @Size(min = 12, max = 200, message = "password must be between 12 and 200 characters")
        String password) {
}
