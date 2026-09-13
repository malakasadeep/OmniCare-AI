package com.omnicare.platform.tenant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnicare.platform.TestcontainersConfiguration;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import com.omnicare.platform.shared.security.AuthenticatedUser;
import com.omnicare.platform.shared.security.JwtProperties;
import com.omnicare.platform.shared.security.JwtService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Day 4's "done when", as a test rather than a curl session: register, then
 * log in, then reach an authenticated endpoint — and be refused without a
 * usable token.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "omnicare.jwt.secret=" + AuthIntegrationTest.TEST_SECRET,
        "omnicare.jwt.access-token-ttl=15m",
        "omnicare.jwt.refresh-token-ttl=30d"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {

    static final String TEST_SECRET = "integration-test-signing-secret-at-least-32-bytes";

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    /** A fresh address per test so tests never collide on the global unique email. */
    private static String uniqueEmail() {
        return "owner-" + UUID.randomUUID() + "@acme.test";
    }

    private JsonNode register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Acme Ltd","email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Nested
    class Register {

        @Test
        void createsATenantAndItsOwnerAndReturnsATokenPair() throws Exception {
            JsonNode tokens = register(uniqueEmail());

            assertThat(tokens.get("accessToken").asText()).isNotBlank();
            assertThat(tokens.get("refreshToken").asText()).isNotBlank();
            assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
            assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900L);
        }

        @Test
        void theNewUserIsTheOwnerOfTheNewTenant() throws Exception {
            String email = uniqueEmail();
            String accessToken = register(email).get("accessToken").asText();

            mvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.role").value("OWNER"))
                    .andExpect(jsonPath("$.companyName").value("Acme Ltd"));
        }

        @Test
        void refusesAnAddressThatIsAlreadyRegistered() throws Exception {
            String email = uniqueEmail();
            register(email);

            mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"companyName":"Other Ltd","email":"%s","password":"%s"}
                                    """.formatted(email, PASSWORD)))
                    .andExpect(status().isConflict());
        }

        @Test
        void rejectsAMalformedEmail() throws Exception {
            mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"companyName":"Acme Ltd","email":"not-an-email","password":"%s"}
                                    """.formatted(PASSWORD)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsAPasswordThatIsTooShort() throws Exception {
            mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"companyName":"Acme Ltd","email":"%s","password":"short"}
                                    """.formatted(uniqueEmail())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsABlankCompanyName() throws Exception {
            mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"companyName":"  ","email":"%s","password":"%s"}
                                    """.formatted(uniqueEmail(), PASSWORD)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Login {

        @Test
        void returnsATokenPairForTheRightPassword() throws Exception {
            String email = uniqueEmail();
            register(email);

            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"%s"}
                                    """.formatted(email, PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty());
        }

        @Test
        void refusesTheWrongPassword() throws Exception {
            String email = uniqueEmail();
            register(email);

            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"not the password"}
                                    """.formatted(email)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void refusesAnUnknownAddress() throws Exception {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"%s"}
                                    """.formatted(uniqueEmail(), PASSWORD)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void doesNotRevealWhetherTheAddressExists() throws Exception {
            String known = uniqueEmail();
            register(known);

            String wrongPassword = mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"not the password"}
                                    """.formatted(known)))
                    .andReturn().getResponse().getContentAsString();

            String unknownUser = mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"%s"}
                                    """.formatted(uniqueEmail(), PASSWORD)))
                    .andReturn().getResponse().getContentAsString();

            // Byte-identical, not merely "both 401": a difference in detail text
            // or body length would still say which account exists.
            assertThat(wrongPassword).isEqualTo(unknownUser);
            assertThat(json.readTree(wrongPassword).get("detail").asText())
                    .isEqualTo("Invalid email or password");
        }
    }

    @Nested
    class ProtectedEndpoint {

        @Test
        void refusesARequestWithNoToken() throws Exception {
            mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        }

        @Test
        void refusesAnExpiredToken() throws Exception {
            String email = uniqueEmail();
            String tenantId = json.readTree(mvc.perform(get("/api/me")
                            .header("Authorization", "Bearer " + register(email).get("accessToken").asText()))
                    .andReturn().getResponse().getContentAsString()).get("tenantId").asText();

            JwtService longAgo = new JwtService(
                    new JwtProperties(TEST_SECRET, Duration.ofMinutes(15), Duration.ofDays(30)),
                    Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));
            String stale = longAgo.issueAccessToken(new AuthenticatedUser(
                    UserId.generate(), new TenantId(UUID.fromString(tenantId)), "OWNER"));

            mvc.perform(get("/api/me").header("Authorization", "Bearer " + stale))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void refusesAGarbageToken() throws Exception {
            mvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void refusesATokenSignedWithTheWrongSecret() throws Exception {
            JwtService attacker = new JwtService(
                    new JwtProperties("a-completely-different-secret-at-least-32-bytes",
                            Duration.ofMinutes(15), Duration.ofDays(30)),
                    Clock.systemUTC());
            String forged = attacker.issueAccessToken(
                    new AuthenticatedUser(UserId.generate(), TenantId.generate(), "OWNER"));

            mvc.perform(get("/api/me").header("Authorization", "Bearer " + forged))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void refusesARefreshTokenPresentedAsAnAccessToken() throws Exception {
            String refresh = register(uniqueEmail()).get("refreshToken").asText();

            mvc.perform(get("/api/me").header("Authorization", "Bearer " + refresh))
                    .andExpect(status().isUnauthorized());
        }
    }
}
