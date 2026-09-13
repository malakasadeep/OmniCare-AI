package com.omnicare.platform.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");

    private static final String SECRET = "a-test-signing-secret-that-is-long-enough-for-hs256";
    private static final String OTHER_SECRET = "a-different-signing-secret-also-long-enough-for-hs256";

    private static final AuthenticatedUser PRINCIPAL =
            new AuthenticatedUser(UserId.generate(), TenantId.generate(), "OWNER");

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(30));
    }

    private static JwtService serviceAt(Instant instant) {
        return new JwtService(properties(SECRET), Clock.fixed(instant, ZoneOffset.UTC));
    }

    private final JwtService service = serviceAt(NOW);

    @Nested
    class AccessTokens {

        @Test
        void parseReturnsTheSamePrincipalThatWasIssued() {
            String token = service.issueAccessToken(PRINCIPAL);

            assertThat(service.parseAccessToken(token)).isEqualTo(PRINCIPAL);
        }

        @Test
        void twoTokensForDifferentUsersDoNotCollide() {
            AuthenticatedUser other =
                    new AuthenticatedUser(UserId.generate(), TenantId.generate(), "AGENT");

            assertThat(service.parseAccessToken(service.issueAccessToken(other))).isEqualTo(other);
        }

        @Test
        void aTokenIsStillValidJustBeforeItsTtlElapses() {
            String token = service.issueAccessToken(PRINCIPAL);

            JwtService later = serviceAt(NOW.plus(Duration.ofMinutes(14)));

            assertThat(later.parseAccessToken(token)).isEqualTo(PRINCIPAL);
        }

        @Test
        void anExpiredTokenIsRejected() {
            String token = service.issueAccessToken(PRINCIPAL);

            JwtService later = serviceAt(NOW.plus(Duration.ofMinutes(16)));

            assertThatThrownBy(() -> later.parseAccessToken(token))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void aRefreshTokenIsNotAcceptedWhereAnAccessTokenIsRequired() {
            String refresh = service.issueRefreshToken(PRINCIPAL);

            assertThatThrownBy(() -> service.parseAccessToken(refresh))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    class RefreshTokens {

        @Test
        void parseReturnsTheSamePrincipalThatWasIssued() {
            String token = service.issueRefreshToken(PRINCIPAL);

            assertThat(service.parseRefreshToken(token)).isEqualTo(PRINCIPAL);
        }

        @Test
        void outlivesAnAccessToken() {
            String refresh = service.issueRefreshToken(PRINCIPAL);

            JwtService later = serviceAt(NOW.plus(Duration.ofDays(29)));

            assertThat(later.parseRefreshToken(refresh)).isEqualTo(PRINCIPAL);
        }

        @Test
        void isRejectedOnceItsOwnTtlElapses() {
            String refresh = service.issueRefreshToken(PRINCIPAL);

            JwtService later = serviceAt(NOW.plus(Duration.ofDays(31)));

            assertThatThrownBy(() -> later.parseRefreshToken(refresh))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void anAccessTokenIsNotAcceptedWhereARefreshTokenIsRequired() {
            String access = service.issueAccessToken(PRINCIPAL);

            assertThatThrownBy(() -> service.parseRefreshToken(access))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    class Forgery {

        @Test
        void aTokenSignedWithADifferentSecretIsRejected() {
            JwtService attacker = new JwtService(
                    properties(OTHER_SECRET), Clock.fixed(NOW, ZoneOffset.UTC));
            String forged = attacker.issueAccessToken(PRINCIPAL);

            assertThatThrownBy(() -> service.parseAccessToken(forged))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void aTamperedPayloadIsRejected() {
            String token = service.issueAccessToken(PRINCIPAL);
            String[] parts = token.split("\\.");
            String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "XY."
                    + parts[2];

            assertThatThrownBy(() -> service.parseAccessToken(tampered))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void anUnsignedTokenIsRejected() {
            String noneAlgToken =
                    "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhdHRhY2tlciJ9.";

            assertThatThrownBy(() -> service.parseAccessToken(noneAlgToken))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void garbageIsRejected() {
            assertThatThrownBy(() -> service.parseAccessToken("not-a-jwt"))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void anEmptyTokenIsRejected() {
            assertThatThrownBy(() -> service.parseAccessToken(""))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    class Configuration {

        @Test
        void refusesASecretTooShortToSignHs256Safely() {
            JwtProperties weak = new JwtProperties(
                    "too-short", Duration.ofMinutes(15), Duration.ofDays(30));

            assertThatThrownBy(() -> new JwtService(weak, Clock.systemUTC()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
