package com.omnicare.platform.shared.security;

import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Issues and verifies the platform's JWTs.
 *
 * <p>Stateless by design: nothing about a session is stored server side, so any
 * instance can verify any token and there is no session table to scale. The
 * price is that an issued token cannot be revoked before it expires, which is
 * why the access token TTL is short and the long-lived one is a refresh token
 * that is only ever presented to the refresh endpoint.
 *
 * <p>The {@link Clock} is injected rather than read from the system so expiry
 * is testable without sleeping.
 */
public class JwtService {

    /**
     * An HMAC key must be at least as long as the hash it keys, so 32 bytes is
     * the floor for HS256. {@code Keys.hmacShaKeyFor} then picks the strongest
     * algorithm the given key actually supports — a 48 byte secret yields HS384
     * rather than HS256 — so this is a minimum, not the algorithm.
     */
    private static final int MINIMUM_SECRET_BYTES = 32;

    private static final String CLAIM_TENANT_ID = "tid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "typ";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");

        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "omnicare.jwt.secret must be at least %d bytes for HS256, was %d"
                            .formatted(MINIMUM_SECRET_BYTES, secretBytes.length));
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenTtl = Objects.requireNonNull(properties.accessTokenTtl(), "accessTokenTtl");
        this.refreshTokenTtl = Objects.requireNonNull(properties.refreshTokenTtl(), "refreshTokenTtl");
    }

    public String issueAccessToken(AuthenticatedUser user) {
        return issue(user, TYPE_ACCESS, accessTokenTtl);
    }

    public String issueRefreshToken(AuthenticatedUser user) {
        return issue(user, TYPE_REFRESH, refreshTokenTtl);
    }

    public AuthenticatedUser parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS);
    }

    public AuthenticatedUser parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH);
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    private String issue(AuthenticatedUser user, String tokenType, Duration ttl) {
        Objects.requireNonNull(user, "user must not be null");
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.userId().value().toString())
                .claim(CLAIM_TENANT_ID, user.tenantId().value().toString())
                .claim(CLAIM_ROLE, user.role())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies signature and expiry, then checks the token is the kind the
     * caller asked for. Without that last check a refresh token — which is
     * long-lived by design — would be accepted as an access token, quietly
     * turning a 15 minute window into a 30 day one.
     */
    private AuthenticatedUser parse(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token is missing");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Token is not valid", e);
        }

        if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new InvalidTokenException("Token is not valid");
        }

        try {
            return new AuthenticatedUser(
                    new UserId(UUID.fromString(claims.getSubject())),
                    new TenantId(UUID.fromString(claims.get(CLAIM_TENANT_ID, String.class))),
                    claims.get(CLAIM_ROLE, String.class));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Token is not valid", e);
        }
    }
}
