package com.omnicare.platform.shared.api;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/**
 * The requested resource is not reachable by this caller.
 *
 * <p>Used for "does not exist" and "belongs to another tenant" alike — with row
 * level security in place those two arrive as the same empty result anyway, and
 * a caller who could tell them apart could probe which ids exist.
 */
public class ResourceNotFoundException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String what) {
        super(HttpStatus.NOT_FOUND, what + " not found");
    }
}
