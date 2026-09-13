package com.omnicare.platform.shared.tenancy;

import com.omnicare.platform.shared.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Copies the tenant out of the verified JWT into {@link TenantContext} for the
 * duration of the request.
 *
 * <p>Runs after {@code JwtAuthenticationFilter}, and takes the tenant from the
 * authenticated principal rather than from a header or a path variable. A tenant
 * the caller can type is a tenant the caller can change.
 */
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            TenantContext.set(user.tenantId());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are pooled and reused. Skipping this hands the
            // next request on this thread the previous request's tenant.
            TenantContext.clear();
        }
    }
}
