package com.omnicare.platform.integration.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LlmStreamingTest {

    private static final LlmRequest REQUEST = new LlmRequest(
            List.of(LlmMessage.user("count to three")), "some-model", 0.3, 256);

    @Nested
    class Fake {

        private final FakeLlmProvider provider = new FakeLlmProvider();

        @Test
        void emitsMoreThanOneChunk() {
            long chunks = provider.streamChat(REQUEST).count().block(Duration.ofSeconds(5));

            assertThat(chunks).isGreaterThan(1);
        }

        /**
         * The streamed and non-streamed paths must not drift apart, or a bug
         * will only ever show up in one of them.
         */
        @Test
        void theConcatenatedChunksEqualTheNonStreamedAnswer() {
            String streamed = String.join("",
                    provider.streamChat(REQUEST).collectList().block(Duration.ofSeconds(5)));

            assertThat(streamed).isEqualTo(provider.chat(REQUEST).content());
        }

        @Test
        void noChunkIsEmptySoEveryEventCarriesSomething() {
            List<String> chunks = provider.streamChat(REQUEST).collectList().block(Duration.ofSeconds(5));

            assertThat(chunks).isNotEmpty().allSatisfy(chunk -> assertThat(chunk).isNotEmpty());
        }
    }

    @Nested
    class Groq {

        /** Replies with a canned {@code text/event-stream} body. */
        private static final class StreamStub implements ExchangeFunction {

            private final String sseBody;
            final AtomicReference<String> capturedBody = new AtomicReference<>("");

            StreamStub(String sseBody) {
                this.sseBody = sseBody;
            }

            @Override
            public Mono<ClientResponse> exchange(ClientRequest request) {
                MockClientHttpRequest recorder =
                        new MockClientHttpRequest(request.method(), request.url());
                recorder.setWriteHandler(written -> DataBufferUtils.join(written)
                        .doOnNext(buffer -> {
                            capturedBody.set(buffer.toString(StandardCharsets.UTF_8));
                            DataBufferUtils.release(buffer);
                        })
                        .then());
                return request.writeTo(recorder, ExchangeStrategies.withDefaults())
                        .then(Mono.fromSupplier(() -> ClientResponse.create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                                .body(sseBody)
                                .build()));
            }
        }

        private static final String SSE_BODY = """
                data: {"choices":[{"delta":{"role":"assistant","content":"One"}}]}

                data: {"choices":[{"delta":{"content":" two"}}]}

                data: {"choices":[{"delta":{"content":" three"}}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;

        private static GroqProvider providerFor(StreamStub stub) {
            return new GroqProvider(
                    WebClient.builder()
                            .baseUrl("https://api.groq.test/openai/v1")
                            .exchangeFunction(stub)
                            .build(),
                    Duration.ofSeconds(5));
        }

        @Test
        void emitsEachDeltaAsItArrives() {
            StepVerifier.create(providerFor(new StreamStub(SSE_BODY)).streamChat(REQUEST))
                    .expectNext("One")
                    .expectNext(" two")
                    .expectNext(" three")
                    .verifyComplete();
        }

        @Test
        void theDoneSentinelEndsTheStreamRatherThanBecomingAToken() {
            List<String> chunks = providerFor(new StreamStub(SSE_BODY))
                    .streamChat(REQUEST).collectList().block(Duration.ofSeconds(5));

            assertThat(chunks).doesNotContain("[DONE]");
        }

        @Test
        void deltasWithNoContentAreSkipped() {
            List<String> chunks = providerFor(new StreamStub(SSE_BODY))
                    .streamChat(REQUEST).collectList().block(Duration.ofSeconds(5));

            assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).isNotEmpty());
        }

        @Test
        void asksTheProviderToStream() {
            StreamStub stub = new StreamStub(SSE_BODY);

            providerFor(stub).streamChat(REQUEST).blockLast(Duration.ofSeconds(5));

            assertThat(stub.capturedBody.get()).contains("\"stream\":true");
        }

        @Test
        void unparseableEventsAreSkippedRatherThanKillingTheStream() {
            String withGarbage = """
                    data: {"choices":[{"delta":{"content":"One"}}]}

                    data: not json at all

                    data: {"choices":[{"delta":{"content":" two"}}]}

                    data: [DONE]

                    """;

            StepVerifier.create(providerFor(new StreamStub(withGarbage)).streamChat(REQUEST))
                    .expectNext("One")
                    .expectNext(" two")
                    .verifyComplete();
        }
    }
}
