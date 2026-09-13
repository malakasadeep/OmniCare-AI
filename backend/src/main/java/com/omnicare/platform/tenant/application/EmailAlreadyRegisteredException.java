package com.omnicare.platform.tenant.application;

import com.omnicare.platform.shared.api.ApplicationException;
import java.io.Serial;
import org.springframework.http.HttpStatus;

/** Raised when a registration uses an address that already has an account. */
public class EmailAlreadyRegisteredException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmailAlreadyRegisteredException(String email) {
        super(HttpStatus.CONFLICT, "An account already exists for " + email);
    }
}
