package com.omnicare.platform.integration.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Exercises the provider against a stubbed exchange rather than the real Groq
 * API: what needs testing is the translation in both directions — a neutral
 * {@link LlmRequest} into that vendor's JSON, and that vendor's JSON back into a
 * neutral {@link LlmResponse} — and none of that needs a network or a key.
 */
class GroqProviderTest {

    private static final LlmRequest REQUEST = new LlmRequest(
            List.of(LlmMessage.system("you are helpful"), LlmMessage.user("hello")),
            "some-model",
            0.3,
            256);

    /**
     * Captures the outgoing request and replies with a canned body.
     *
     * <p>The body is read through an explicit write handler:
     * {@code MockClientHttpRequest} does not retain what was written to it
     * otherwise, and {@code getBody()} then reports that the body is not set.
     */
    private static final class StubExchange implements ExchangeFunction {

        private final HttpStatus status;
        private final String body;
        final AtomicReference<ClientRequest> captured = new AtomicReference<>();
        final AtomicReference<String> capturedBody = new AtomicReference<>("");

        StubExchange(HttpStatus status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest request) {
            captured.set(request);
            MockClientHttpRequest recorder = new MockClientHttpRequest(request.method(), request.url());
            recorder.setWriteHandler(written -> DataBufferUtils.join(written)
                    .doOnNext(buffer -> {
                        capturedBody.set(buffer.toString(StandardCharsets.UTF_8));
                        DataBufferUtils.release(buffer);
                    })
                    .then());

            return request.writeTo(recorder, ExchangeStrategies.withDefaults())
                    .then(Mono.fromSupplier(() -> ClientResponse.create(status)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(body)
                            .build()));
        }
    }

    private static GroqProvider providerFor(StubExchange exchange) {
        WebClient client = WebClient.builder()
                .baseUrl("https://api.groq.test/openai/v1")
                .exchangeFunction(exchange)
                .build();
        return new GroqProvider(client, Duration.ofSeconds(5));
    }

    private static final String SUCCESS_BODY = """
            {
              "id": "chatcmpl-1",
              "choices": [
                {"index": 0, "finish_reason": "stop",
                 "message": {"role": "assistant", "content": "Hello, how can I help?"}}
              ],
              "usage": {"prompt_tokens": 18, "completion_tokens": 7, "total_tokens": 25}
            }
            """;

    @Nested
    class Outgoing {

        @Test
        void sendsTheModelTemperatureAndTokenBudgetFromTheRequest() {
            StubExchange exchange = new StubExchange(HttpStatus.OK, SUCCESS_BODY);

            providerFor(exchange).chat(REQUEST);

            String sent = exchange.capturedBody.get();
            assertThat(sent).contains("\"model\":\"some-model\"");
            assertThat(sent).contains("\"temperature\":0.3");
            assertThat(sent).contains("\"max_tokens\":256");
        }

        @Test
        void sendsEachMessageWithItsRoleLowercasedAsThatApiExpects() {
            StubExchange exchange = new StubExchange(HttpStatus.OK, SUCCESS_BODY);

            providerFor(exchange).chat(REQUEST);

            String sent = exchange.capturedBody.get();
            assertThat(sent).contains("\"role\":\"system\"");
            assertThat(sent).contains("\"content\":\"you are helpful\"");
            assertThat(sent).contains("\"role\":\"user\"");
            assertThat(sent).contains("\"content\":\"hello\"");
        }

        @Test
        void postsToTheChatCompletionsPath() {
            StubExchange exchange = new StubExchange(HttpStatus.OK, SUCCESS_BODY);

            providerFor(exchange).chat(REQUEST);

            assertThat(exchange.captured.get().url().getPath()).endsWith("/chat/completions");
        }
    }

    @Nested
    class Incoming {

        @Test
        void returnsTheAssistantContent() {
            LlmResponse response = providerFor(new StubExchange(HttpStatus.OK, SUCCESS_BODY)).chat(REQUEST);

            assertThat(response.content()).isEqualTo("Hello, how can I help?");
        }

        @Test
        void carriesTokenUsageBack() {
            LlmResponse response = providerFor(new StubExchange(HttpStatus.OK, SUCCESS_BODY)).chat(REQUEST);

            assertThat(response.promptTokens()).isEqualTo(18);
            assertThat(response.completionTokens()).isEqualTo(7);
        }

        @Test
        void carriesTheFinishReasonBack() {
            LlmResponse response = providerFor(new StubExchange(HttpStatus.OK, SUCCESS_BODY)).chat(REQUEST);

            assertThat(response.finishReason()).isEqualTo("stop");
        }

        @Test
        void reportsWhenTheAnswerWasCutOffByTheTokenBudget() {
            String truncated = SUCCESS_BODY.replace("\"stop\"", "\"length\"");

            LlmResponse response = providerFor(new StubExchange(HttpStatus.OK, truncated)).chat(REQUEST);

            assertThat(response.wasTruncated()).isTrue();
        }

        @Test
        void aCompletedAnswerIsNotTruncated() {
            LlmResponse response = providerFor(new StubExchange(HttpStatus.OK, SUCCESS_BODY)).chat(REQUEST);

            assertThat(response.wasTruncated()).isFalse();
        }
    }

    @Nested
    class Failures {

        @Test
        void aRateLimitBecomesAProviderException() {
            StubExchange exchange = new StubExchange(HttpStatus.TOO_MANY_REQUESTS,
                    "{\"error\":{\"message\":\"rate limit reached\"}}");

            assertThatThrownBy(() -> providerFor(exchange).chat(REQUEST))
                    .isInstanceOf(LlmProviderException.class);
        }

        @Test
        void aServerErrorBecomesAProviderException() {
            StubExchange exchange = new StubExchange(HttpStatus.BAD_GATEWAY, "{}");

            assertThatThrownBy(() -> providerFor(exchange).chat(REQUEST))
                    .isInstanceOf(LlmProviderException.class);
        }

        @Test
        void aResponseWithNoChoicesBecomesAProviderException() {
            StubExchange exchange = new StubExchange(HttpStatus.OK, "{\"choices\":[]}");

            assertThatThrownBy(() -> providerFor(exchange).chat(REQUEST))
                    .isInstanceOf(LlmProviderException.class);
        }

        @Test
        void theExceptionDoesNotEchoTheProvidersRawErrorBody() {
            StubExchange exchange = new StubExchange(HttpStatus.UNAUTHORIZED,
                    "{\"error\":{\"message\":\"Invalid API Key: gsk_supersecretvalue\"}}");

            assertThatThrownBy(() -> providerFor(exchange).chat(REQUEST))
                    .isInstanceOf(LlmProviderException.class)
                    .hasMessageNotContaining("gsk_supersecretvalue");
        }
    }

    @Test
    void isNamedSoTheFactoryCanSelectIt() {
        assertThat(providerFor(new StubExchange(HttpStatus.OK, SUCCESS_BODY)).name()).isEqualTo("groq");
    }
}
