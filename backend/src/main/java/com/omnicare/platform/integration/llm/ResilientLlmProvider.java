package com.omnicare.platform.integration.llm;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Wraps any {@link LlmProvider} in retries, a circuit breaker and a fallback.
 *
 * <p>A Decorator rather than something baked into each provider: the policies
 * are identical whichever vendor is behind them, and a new provider gets them
 * for free without writing a line of resilience code.
 *
 * <p>What each layer is actually for:
 *
 * <p><b>Retry</b> handles the failure that will not happen again — a rate limit,
 * a momentary 502. Backoff is exponential <em>with jitter</em>, which matters
 * more than it sounds: without jitter every request that failed together retries
 * together, and the synchronised burst is what stops a struggling provider from
 * recovering. Only failures marked retryable are retried at all.
 *
 * <p><b>The circuit breaker</b> handles the failure that will. Once enough recent
 * calls have failed it stops calling out entirely, so a provider that is down
 * costs one fast rejection per visitor instead of the whole retry budget each.
 * After a wait it admits trial calls and closes again if they succeed.
 *
 * <p><b>The fallback</b> is what the visitor sees. A support widget that says
 * "500 Internal Server Error" is worse than useless, so an exhausted or
 * short-circuited call returns a plain sentence admitting the trouble — and
 * never the provider's own message, which quotes API keys back at you.
 */
class ResilientLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ResilientLlmProvider.class);

    /**
     * Deliberately plain, and deliberately not the provider's error text. One
     * language only: this is the one reply that cannot be generated, so it
     * cannot follow the customer's language the way every other reply does.
     */
    static final String FALLBACK_MESSAGE =
            "Sorry — I am having trouble reaching my assistant right now. "
                    + "Please try again in a moment, or ask for a human and someone will take over.";

    static final String FALLBACK_FINISH_REASON = "fallback";

    private final LlmProvider delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    ResilientLlmProvider(LlmProvider delegate, ResilienceSettings settings) {
        this.delegate = delegate;
        this.circuitBreaker = CircuitBreaker.of(delegate.name(), CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.failureRateThreshold())
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumCalls())
                .waitDurationInOpenState(settings.openStateWait())
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build());
        this.retry = Retry.of(delegate.name(), RetryConfig.custom()
                .maxAttempts(settings.maxAttempts())
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialRandomBackoff(settings.initialBackoff(), 2.0, 0.5))
                // Retrying a request the provider has already judged malformed
                // just spends time and money to be told the same thing.
                .retryOnException(ResilientLlmProvider::isWorthRetrying)
                .build());

        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("Language model circuit for {} moved {}",
                        delegate.name(), event.getStateTransition()));
    }

    private static boolean isWorthRetrying(Throwable throwable) {
        return throwable instanceof LlmProviderException e && e.isRetryable();
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        // Order matters: the breaker is inside the retry, so a run of retries
        // counts as the several failures it is rather than one.
        Supplier<LlmResponse> guarded = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> delegate.chat(request)));
        try {
            return guarded.get();
        } catch (CallNotPermittedException e) {
            log.debug("Language model circuit for {} is open; answering with the fallback", name());
            return fallbackResponse();
        } catch (RuntimeException e) {
            log.warn("Language model call to {} failed after retries: {}", name(), e.toString());
            return fallbackResponse();
        }
    }

    @Override
    public Flux<String> streamChat(LlmRequest request) {
        // No retry here on purpose. A stream that fails part-way has already put
        // text in front of the visitor; starting again would repeat it, and
        // resuming from the middle is not something the API supports.
        return delegate.streamChat(request)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(e -> {
                    log.warn("Language model stream from {} failed: {}", name(), e.toString());
                    return Flux.just(FALLBACK_MESSAGE);
                });
    }

    private static LlmResponse fallbackResponse() {
        return new LlmResponse(FALLBACK_MESSAGE, 0, 0, FALLBACK_FINISH_REASON);
    }

    /** Exposed for the health endpoint and tests. */
    CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    static Duration defaultOpenStateWait() {
        return Duration.ofSeconds(30);
    }
}
