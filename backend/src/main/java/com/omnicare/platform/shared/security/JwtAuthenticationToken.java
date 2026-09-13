package com.omnicare.platform.shared.security;

import java.io.Serial;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * An already-authenticated principal, created from a verified JWT.
 *
 * <p>There are no credentials to hold: verifying the signature is the
 * authentication, so this token is born authenticated and never passes through
 * an {@code AuthenticationProvider}.
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient AuthenticatedUser principal;

    public JwtAuthenticationToken(AuthenticatedUser principal,
                                  Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
