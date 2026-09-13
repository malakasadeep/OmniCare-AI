package com.omnicare.platform.integration.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class ResilientLlmProviderTest {

    private static final LlmRequest REQUEST =
            new LlmRequest(List.of(LlmMessage.user("hi")), "some-model", 0.3, 128);

    private static final LlmResponse GOOD = new LlmResponse("a real answer", 5, 5, "stop");

    /** A provider whose behaviour each test dictates, counting how often it is called. */
    private static final class ScriptedProvider implements LlmProvider {

        private final Supplier<LlmResponse> behaviour;
        final AtomicInteger calls = new AtomicInteger();

        ScriptedProvider(Supplier<LlmResponse> behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public String name() {
            return "scripted";
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            calls.incrementAndGet();
            return behaviour.get();
        }

        @Override
        public Flux<String> streamChat(LlmRequest request) {
            calls.incrementAndGet();
            return Flux.defer(() -> {
                try {
                    return Flux.just(behaviour.get().content());
                } catch (RuntimeException e) {
                    return Flux.error(e);
                }
            });
        }
    }

    /**
     * Fast settings, so the tests exercise the policies without waiting on them.
     *
     * <p>{@code minimumCalls} is 6 against {@code maxAttempts} 3 for a reason the
     * tests found the hard way: the breaker counts attempts, not requests, so a
     * minimum at or below the retry budget lets the circuit open in the middle of
     * one request and cut its own retries short.
     */
    private static ResilienceSettings settings() {
        return new ResilienceSettings(3, Duration.ofMillis(10), 50.0f, 6, 6, Duration.ofMillis(50));
    }

    private static ResilientLlmProvider wrap(LlmProvider delegate) {
        return new ResilientLlmProvider(delegate, settings());
    }

    private static LlmProviderException retryable() {
        return new LlmProviderException("temporarily unavailable", null, true);
    }

    private static LlmProviderException permanent() {
        return new LlmProviderException("bad request", null, false);
    }

    @Nested
    class Retrying {

        @Test
        void aTransientFailureIsRetriedAndTheAnswerStillArrives() {
            AtomicInteger attempt = new AtomicInteger();
            ScriptedProvider flaky = new ScriptedProvider(() -> {
                if (attempt.incrementAndGet() < 3) {
                    throw retryable();
                }
                return GOOD;
            });

            LlmResponse response = wrap(flaky).chat(REQUEST);

            assertThat(response.content()).isEqualTo("a real answer");
            assertThat(flaky.calls).hasValue(3);
        }

        @Test
        void retriesStopAtTheConfiguredLimit() {
            ScriptedProvider alwaysFailing = new ScriptedProvider(() -> {
                throw retryable();
            });

            wrap(alwaysFailing).chat(REQUEST);

            assertThat(alwaysFailing.calls).hasValue(3);
        }

        /**
         * A malformed request will be just as malformed the second time. Retrying
         * it wastes the visitor's time and, on a paid API, their money.
         */
        @Test
        void aPermanentFailureIsNotRetried() {
            ScriptedProvider refusing = new ScriptedProvider(() -> {
                throw permanent();
            });

            wrap(refusing).chat(REQUEST);

            assertThat(refusing.calls).hasValue(1);
        }

        @Test
        void aSuccessfulCallIsMadeExactlyOnce() {
            ScriptedProvider healthy = new ScriptedProvider(() -> GOOD);

            wrap(healthy).chat(REQUEST);

            assertThat(healthy.calls).hasValue(1);
        }
    }

    @Nested
    class FallingBack {

        @Test
        void aFailingProviderYieldsAPoliteAnswerRatherThanAnException() {
            LlmResponse response = wrap(new ScriptedProvider(() -> {
                throw retryable();
            })).chat(REQUEST);

            assertThat(response.content()).isNotBlank();
            assertThat(response.finishReason()).isEqualTo("fallback");
        }

        @Test
        void theFallbackDoesNotLeakTheProvidersErrorToTheVisitor() {
            LlmResponse response = wrap(new ScriptedProvider(() -> {
                throw new LlmProviderException(
                        "401 Unauthorized: Invalid API Key gsk_secret", null, false);
            })).chat(REQUEST);

            assertThat(response.content())
                    .doesNotContain("gsk_secret")
                    .doesNotContain("401");
        }

        @Test
        void aFailingStreamEndsWithTheSamePoliteAnswerInsteadOfAnError() {
            List<String> chunks = wrap(new ScriptedProvider(() -> {
                throw retryable();
            })).streamChat(REQUEST).collectList().block(Duration.ofSeconds(5));

            assertThat(chunks).isNotEmpty();
            assertThat(String.join("", chunks)).isNotBlank();
        }

        @Test
        void aHealthyStreamIsPassedStraightThrough() {
            List<String> chunks = wrap(new ScriptedProvider(() -> GOOD))
                    .streamChat(REQUEST).collectList().block(Duration.ofSeconds(5));

            assertThat(String.join("", chunks)).isEqualTo("a real answer");
        }
    }

    @Nested
    class BreakingTheCircuit {

        /**
         * The point of the breaker: once a provider is clearly down, stop paying
         * the timeout on every request. Without it each visitor waits the full
         * retry budget before being told the same thing.
         */
        @Test
        void onceOpenTheProviderIsNoLongerCalledAtAll() {
            ScriptedProvider down = new ScriptedProvider(() -> {
                throw retryable();
            });
            ResilientLlmProvider resilient = wrap(down);

            for (int i = 0; i < 5; i++) {
                resilient.chat(REQUEST);
            }
            int callsWhileFailing = down.calls.get();

            resilient.chat(REQUEST);

            assertThat(down.calls.get())
                    .as("no further calls once the circuit is open")
                    .isEqualTo(callsWhileFailing);
        }

        @Test
        void aShortCircuitedCallStillAnswersTheVisitorPolitely() {
            ResilientLlmProvider resilient = wrap(new ScriptedProvider(() -> {
                throw retryable();
            }));
            for (int i = 0; i < 5; i++) {
                resilient.chat(REQUEST);
            }

            LlmResponse response = resilient.chat(REQUEST);

            assertThat(response.content()).isNotBlank();
            assertThat(response.finishReason()).isEqualTo("fallback");
        }

        @Test
        void theCircuitClosesAgainOnceTheProviderRecovers() throws Exception {
            AtomicInteger failuresLeft = new AtomicInteger(20);
            ScriptedProvider recovering = new ScriptedProvider(() -> {
                if (failuresLeft.decrementAndGet() > 0) {
                    throw retryable();
                }
                return GOOD;
            });
            ResilientLlmProvider resilient = wrap(recovering);

            for (int i = 0; i < 5; i++) {
                resilient.chat(REQUEST);
            }
            failuresLeft.set(0);

            // Past the open-state wait, the breaker admits trial calls again.
            Thread.sleep(120);

            assertThat(resilient.chat(REQUEST).content()).isEqualTo("a real answer");
        }

        @Test
        void settingsRefuseAMinimumThatWouldOpenTheCircuitMidRequest() {
            assertThatThrownBy(() -> new ResilienceSettings(
                    3, Duration.ofMillis(10), 50.0f, 6, 3, Duration.ofMillis(50)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minimumCalls");
        }

        @Test
        void theDelegatesNameIsPreservedSoConfigurationStillSelectsIt() {
            assertThat(wrap(new ScriptedProvider(() -> GOOD)).name()).isEqualTo("scripted");
        }
    }
}
