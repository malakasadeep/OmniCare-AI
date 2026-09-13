package com.omnicare.platform.tenant.api;

import com.omnicare.platform.tenant.application.TokenPair;

/** The token payload returned by register and login. */
public record TokenResponse(String tokenType, String accessToken, String refreshToken, long expiresIn) {

    static TokenResponse from(TokenPair tokens) {
        return new TokenResponse(
                "Bearer",
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.expiresInSeconds());
    }
}
