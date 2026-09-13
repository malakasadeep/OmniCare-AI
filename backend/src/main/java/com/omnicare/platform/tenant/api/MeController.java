package com.omnicare.platform.tenant.api;

import com.omnicare.platform.shared.security.AuthenticatedUser;
import com.omnicare.platform.tenant.domain.TenantRepository;
import com.omnicare.platform.tenant.domain.User;
import com.omnicare.platform.tenant.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class MeController {

    private final UserRepository users;
    private final TenantRepository tenants;

    MeController(UserRepository users, TenantRepository tenants) {
        this.users = users;
        this.tenants = tenants;
    }

    /**
     * The token says who the caller claims to be; the database says whether that
     * is still true. A user deleted after their token was issued must not keep
     * working for the rest of the token's life.
     */
    @GetMapping("/api/me")
    MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String companyName = tenants.findById(user.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .name();
        return new MeResponse(
                user.id().value(),
                user.tenantId().value(),
                user.email().value(),
                user.role().name(),
                companyName);
    }
}
