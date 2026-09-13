package com.omnicare.platform.tenant.application;

import com.omnicare.platform.shared.domain.Email;
import com.omnicare.platform.shared.security.AuthenticatedUser;
import com.omnicare.platform.shared.security.JwtService;
import com.omnicare.platform.tenant.domain.Plan;
import com.omnicare.platform.tenant.domain.Tenant;
import com.omnicare.platform.tenant.domain.TenantRepository;
import com.omnicare.platform.tenant.domain.User;
import com.omnicare.platform.tenant.domain.UserRepository;
import com.omnicare.platform.tenant.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and login. Orchestrates the domain; holds no rules of its own. */
@Service
public class AuthService {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    AuthService(TenantRepository tenants,
                UserRepository users,
                PasswordEncoder passwordEncoder,
                JwtService jwtService,
                Clock clock) {
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /**
     * Creates a tenant and its first user in one transaction. A tenant with no
     * owner is not a state this system should ever be able to reach, so the two
     * writes either both land or neither does.
     */
    @Transactional
    public TokenPair register(String companyName, Email email, String rawPassword) {
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email.value());
        }
        Instant now = clock.instant();
        Tenant tenant = tenants.save(Tenant.register(companyName, Plan.FREE, now));
        User owner = users.save(User.register(
                tenant.id(),
                email,
                passwordEncoder.encode(rawPassword),
                UserRole.OWNER,
                now));
        return issueFor(owner);
    }

    @Transactional(readOnly = true)
    public TokenPair login(Email email, String rawPassword) {
        User user = users.findByEmail(email).orElse(null);
        // Hash even when there is no such user, so a caller cannot tell the two
        // cases apart by how long the response took.
        if (user == null) {
            passwordEncoder.encode(rawPassword);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueFor(user);
    }

    private TokenPair issueFor(User user) {
        AuthenticatedUser principal =
                new AuthenticatedUser(user.id(), user.tenantId(), user.role().name());
        return new TokenPair(
                jwtService.issueAccessToken(principal),
                jwtService.issueRefreshToken(principal),
                jwtService.accessTokenTtl().toSeconds());
    }
}
