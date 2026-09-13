package com.omnicare.platform.tenant.application;

/** The pair of tokens handed out on a successful register or login. */
public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
