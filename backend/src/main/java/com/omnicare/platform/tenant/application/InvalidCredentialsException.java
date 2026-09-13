package com.omnicare.platform.tenant.application;

import com.omnicare.platform.shared.api.ApplicationException;
import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * Raised for a failed login.
 *
 * <p>One type for both "no such address" and "wrong password", with a message
 * that does not say which: distinguishing them turns the login endpoint into a
 * way to enumerate who has an account.
 */
public class InvalidCredentialsException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
