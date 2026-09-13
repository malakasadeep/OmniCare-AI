package com.omnicare.platform.conversation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnicare.platform.TestcontainersConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * Day 8's "done when": text arrives incrementally rather than all at once.
 *
 * <p>Against a real server rather than MockMvc. SSE here is a servlet async
 * response, and MockMvc neither performs the async dispatch nor lets a client
 * hang up mid-stream, which is precisely the behaviour under test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "omnicare.jwt.secret=" + ConversationStreamIntegrationTest.TEST_SECRET,
                "omnicare.jwt.access-token-ttl=15m",
                "omnicare.jwt.refresh-token-ttl=30d",
                "omnicare.llm.provider=fake"
        })
class ConversationStreamIntegrationTest {

    static final String TEST_SECRET = "streaming-test-signing-secret-at-least-32-bytes";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper json;

    private WebClient client;
    private String token;
    private String conversationId;

    /** Captures what the global handler logs, so disconnect noise is testable. */
    private ListAppender<ILoggingEvent> handlerLog;

    @BeforeEach
    void captureHandlerLogging() {
        handlerLog = new ListAppender<>();
        handlerLog.start();
        ((Logger) LoggerFactory.getLogger(
                "com.omnicare.platform.shared.api.ApiExceptionHandler")).addAppender(handlerLog);
    }

    @AfterEach
    void stopCapturingHandlerLogging() {
        ((Logger) LoggerFactory.getLogger(
                "com.omnicare.platform.shared.api.ApiExceptionHandler")).detachAppender(handlerLog);
    }

    @BeforeEach
    void registerAndStartAConversation() {
        client = WebClient.builder().baseUrl("http://localhost:" + port).build();

        Map<?, ?> tokens = client.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"companyName":"Acme Ltd","email":"owner-%s@acme.test",
                         "password":"correct horse battery staple"}
                        """.formatted(UUID.randomUUID()))
                .retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(20));
        token = (String) tokens.get("accessToken");

        Map<?, ?> conversation = client.post().uri("/api/conversations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(20));
        conversationId = (String) conversation.get("id");
    }

    private Flux<ServerSentEvent<String>> openStream(String message) {
        return client.get()
                .uri(builder -> builder.path("/api/conversations/{id}/stream")
                        .queryParam("message", message)
                        .build(conversationId))
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                });
    }

    private List<JsonNode> transcript() {
        String body = client.get()
                .uri("/api/conversations/{id}/messages", conversationId)
                .header("Authorization", "Bearer " + token)
                .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(20));
        try {
            return List.of(json.readValue(body, JsonNode[].class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tokenTextOf(ServerSentEvent<String> event, ObjectMapper mapper) {
        try {
            return mapper.readTree(event.data()).get("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theAnswerArrivesAsManyEventsRatherThanOne() {
        List<ServerSentEvent<String>> events =
                openStream("hello there").collectList().block(Duration.ofSeconds(30));

        List<ServerSentEvent<String>> tokens = events.stream()
                .filter(event -> "token".equals(event.event()))
                .toList();

        assertThat(tokens).hasSizeGreaterThan(1);
    }

    @Test
    void theStreamIsTerminatedByADoneEvent() {
        List<ServerSentEvent<String>> events =
                openStream("hello there").collectList().block(Duration.ofSeconds(30));

        assertThat(events.get(events.size() - 1).event()).isEqualTo("done");
    }

    @Test
    void theConcatenatedTokensAreTheWholeAnswer() {
        List<ServerSentEvent<String>> events =
                openStream("where is my order?").collectList().block(Duration.ofSeconds(30));

        String assembled = events.stream()
                .filter(event -> "token".equals(event.event()))
                .map(event -> tokenTextOf(event, json))
                .reduce("", String::concat);

        assertThat(assembled).contains("[fake-llm]").contains("where is my order?");
    }

    /**
     * Guards the reason tokens are sent as JSON. SSE strips one space after
     * {@code data:}, so a raw fragment beginning with a space would arrive
     * without it and the answer would read "wordsruntogether".
     */
    @Test
    void leadingSpacesInsideTokensSurviveTheWireFormat() {
        List<String> tokens = openStream("spacing check")
                .filter(event -> "token".equals(event.event()))
                .map(event -> tokenTextOf(event, json))
                .collectList().block(Duration.ofSeconds(30));

        assertThat(String.join("", tokens))
                .as("a token boundary must not swallow a space")
                .contains("fake-llm] I received");
    }

    @Test
    void theVisitorMessageAndTheCompletedReplyAreBothPersisted() {
        openStream("please remember this").blockLast(Duration.ofSeconds(30));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<JsonNode> messages = transcript();
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).get("role").asText()).isEqualTo("USER");
            assertThat(messages.get(0).get("content").asText()).isEqualTo("please remember this");
            assertThat(messages.get(1).get("role").asText()).isEqualTo("ASSISTANT");
            assertThat(messages.get(1).get("content").asText()).contains("[fake-llm]");
        });
    }

    /**
     * The visitor closed the tab part-way through. What they saw should still be
     * in the transcript, so the next turn — and the operator reading it later —
     * see the same conversation the visitor did.
     */
    @Test
    void hangingUpMidStreamStillPersistsWhatWasAlreadySent() {
        // Long enough to be split into many fragments, so hanging up after three
        // of them is unambiguously early rather than a race with the last one.
        String longMessage = "cut me off ".repeat(30).trim();

        List<String> received = openStream(longMessage)
                .filter(event -> "token".equals(event.event()))
                .map(event -> tokenTextOf(event, json))
                .take(3)
                .collectList()
                .block(Duration.ofSeconds(30));

        String seenByVisitor = String.join("", received);
        String wholeAnswer = "[fake-llm] I received 2 message(s). You said: " + longMessage;

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<JsonNode> messages = transcript();
            assertThat(messages).hasSize(2);

            String stored = messages.get(1).get("content").asText();

            // The server may be a fragment or two ahead of what the visitor
            // actually received, since cancellation takes a moment to travel
            // back. What must hold is that the stored text is what was produced,
            // starts with what they saw, and stops short of the full answer.
            assertThat(stored).isNotEmpty().startsWith(seenByVisitor);
            assertThat(wholeAnswer).startsWith(stored);
            assertThat(stored.length()).isLessThan(wholeAnswer.length());
        });
    }

    /**
     * A visitor closing the tab is ordinary, not a fault. Left to the catch-all
     * it arrives as an IOException and is logged at ERROR with a stack trace —
     * so a busy day would bury real errors under one per closed tab.
     */
    @Test
    void aVisitorHangingUpIsNotLoggedAsAnError() {
        openStream("cut me off ".repeat(30).trim())
                .filter(event -> "token".equals(event.event()))
                .take(2)
                .collectList()
                .block(Duration.ofSeconds(30));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(handlerLog.list)
                        .extracting(ILoggingEvent::getLevel)
                        .doesNotContain(Level.ERROR));
    }

    @Test
    void streamingRequiresAToken() {
        Flux<ServerSentEvent<String>> unauthenticated = WebClient.builder()
                .baseUrl("http://localhost:" + port).build()
                .get()
                .uri(builder -> builder.path("/api/conversations/{id}/stream")
                        .queryParam("message", "hi").build(conversationId))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                });

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> unauthenticated.blockLast(Duration.ofSeconds(20)))).isNotNull();
    }
}
