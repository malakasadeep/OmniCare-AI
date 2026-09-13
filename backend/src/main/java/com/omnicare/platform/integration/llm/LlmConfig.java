package com.omnicare.platform.integration.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** Builds the available providers from configuration. */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
class LlmConfig {

    @Bean
    LlmProvider fakeLlmProvider() {
        return new FakeLlmProvider();
    }

    /**
     * Only built when a key is actually present.
     *
     * <p>The condition tests for a non-blank value rather than using
     * {@code @ConditionalOnProperty(name = "api-key")}, which would be wrong
     * here: the key is bound as {@code ${GROQ_API_KEY:}}, so with the variable
     * unset the property is not missing but present and empty, and that
     * condition matches. The provider would then be built with an empty bearer
     * token and fail on the first visitor's message with a 401 from the vendor.
     *
     * <p>Absent instead, the factory refuses to start when groq was the selected
     * provider — so a missing key is a startup failure naming the problem.
     */
    @Bean
    @ConditionalOnExpression("!'${omnicare.llm.groq.api-key:}'.isBlank()")
    LlmProvider groqProvider(LlmProperties properties, WebClient.Builder builder) {
        WebClient client = builder
                .baseUrl(properties.groq().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.groq().apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(timeoutingHttpClient(properties)))
                .build();
        return new GroqProvider(client, properties.timeout());
    }

    /**
     * Both timeouts set explicitly, because the defaults are effectively
     * infinite. A request that never returns holds its thread until the socket
     * dies of old age, and enough of those is an outage caused by someone else's.
     *
     * <p>{@code responseTimeout} is applied per read rather than to the whole
     * exchange, which is what lets it coexist with SSE: a stream that keeps
     * producing tokens keeps resetting it, while one that has silently stalled
     * still gets cut.
     */
    private static HttpClient timeoutingHttpClient(LlmProperties properties) {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) properties.connectTimeout().toMillis())
                .responseTimeout(properties.timeout());
    }
}
