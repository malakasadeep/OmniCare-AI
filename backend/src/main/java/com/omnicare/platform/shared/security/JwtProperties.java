package com.omnicare.platform.shared.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the {@code omnicare.jwt.*} configuration block.
 *
 * <p>A record, so the values are immutable once the context has started and
 * nothing can retune the token lifetime at runtime.
 */
@ConfigurationProperties(prefix = "omnicare.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
