package com.omnicare.platform.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a {@code Authorization: Bearer <jwt>} header into an authenticated
 * {@code SecurityContext} for the rest of the request.
 *
 * <p>A missing or unusable token is not rejected here — the filter simply leaves
 * the context empty and lets the chain continue. Whether an anonymous request is
 * allowed is {@link SecurityConfig}'s decision, not this filter's, which is what
 * keeps the public endpoints public without this class knowing their paths.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AuthenticatedUser user = jwtService.parseAccessToken(token);
                var authentication = new JwtAuthenticationToken(
                        user, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException e) {
                // Left anonymous on purpose; the entry point turns that into a 401.
                log.debug("Rejected bearer token on {}: {}", request.getRequestURI(), e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
