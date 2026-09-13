package com.omnicare.platform.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.omnicare.platform.TestcontainersConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Runs against a real servlet container rather than MockMvc, because the bug
 * this guards is invisible to MockMvc.
 *
 * <p>When a controller throws, the container performs a second, internal
 * dispatch to {@code /error}. The security filters are
 * {@code OncePerRequestFilter}s, so they do not run again and that dispatch
 * carries no security context — which means a rule of
 * {@code anyRequest().authenticated()} rejects it and the client sees 401 in
 * place of whatever status the controller actually chose. MockMvc does not
 * perform the error dispatch at all, so it reports the intended status and the
 * whole problem stays hidden until something makes a real HTTP call.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "omnicare.jwt.secret=" + ErrorDispatchIntegrationTest.TEST_SECRET,
                "omnicare.jwt.access-token-ttl=15m",
                "omnicare.jwt.refresh-token-ttl=30d"
        })
class ErrorDispatchIntegrationTest {

    static final String TEST_SECRET = "error-dispatch-test-signing-secret-32-bytes-plus";

    @Autowired
    private TestRestTemplate rest;

    @SuppressWarnings("unchecked")
    private String registerAndGetAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"companyName":"Acme Ltd","email":"%s","password":"correct horse battery staple"}
                """.formatted("owner-" + UUID.randomUUID() + "@acme.test");

        ResponseEntity<Map> response =
                rest.postForEntity("/api/auth/register", new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("accessToken");
    }

    private ResponseEntity<String> get(String path, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void anAuthenticatedRequestForAMissingConversationIs404NotA401FromTheErrorDispatch() {
        String token = registerAndGetAccessToken();

        ResponseEntity<String> response = get("/api/conversations/" + UUID.randomUUID(), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anUnauthenticatedRequestIsStill401() {
        ResponseEntity<String> response = get("/api/conversations/" + UUID.randomUUID(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anAuthenticatedRequestToAPathThatDoesNotExistIs404() {
        String token = registerAndGetAccessToken();

        ResponseEntity<String> response = get("/api/no-such-endpoint", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theHealthEndpointStaysPublic() {
        ResponseEntity<String> response = get("/actuator/health", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
