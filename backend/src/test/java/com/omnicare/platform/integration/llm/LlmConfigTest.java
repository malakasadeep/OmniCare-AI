package com.omnicare.platform.integration.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Which providers exist for a given configuration.
 *
 * <p>Worth its own test because the obvious spelling is wrong. The key is bound
 * as {@code ${GROQ_API_KEY:}}, so when the variable is unset the property is not
 * missing — it is present and empty, and a plain
 * {@code @ConditionalOnProperty(name = "api-key")} matches it happily. The Groq
 * provider would then be built with an empty bearer token and fail on the first
 * visitor's message with a 401 from the vendor, which is a much worse place to
 * discover a missing key than startup.
 */
class LlmConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebClientAutoConfiguration.class))
            .withUserConfiguration(LlmConfig.class)
            .withPropertyValues(
                    "omnicare.llm.model=some-model",
                    "omnicare.llm.temperature=0.3",
                    "omnicare.llm.max-tokens=256",
                    "omnicare.llm.connect-timeout=5s",
                    "omnicare.llm.timeout=30s",
                    "omnicare.llm.resilience.max-attempts=3",
                    "omnicare.llm.resilience.initial-backoff=10ms",
                    "omnicare.llm.resilience.failure-rate-threshold=50",
                    "omnicare.llm.resilience.sliding-window-size=20",
                    "omnicare.llm.resilience.minimum-calls=10",
                    "omnicare.llm.resilience.open-state-wait=30s",
                    "omnicare.llm.groq.base-url=https://api.groq.test/openai/v1");

    @Test
    void withoutAKeyOnlyTheFakeProviderExists() {
        runner.withPropertyValues("omnicare.llm.provider=fake", "omnicare.llm.groq.api-key=")
                .run(context -> assertThat(context.getBeansOfType(LlmProvider.class).values())
                        .extracting(LlmProvider::name)
                        .containsExactly("fake"));
    }

    @Test
    void aBlankKeyCountsAsNoKey() {
        runner.withPropertyValues("omnicare.llm.provider=fake", "omnicare.llm.groq.api-key=   ")
                .run(context -> assertThat(context.getBeansOfType(LlmProvider.class).values())
                        .extracting(LlmProvider::name)
                        .containsExactly("fake"));
    }

    @Test
    void withAKeyTheGroqProviderIsAvailableToo() {
        runner.withPropertyValues("omnicare.llm.provider=fake", "omnicare.llm.groq.api-key=gsk_test")
                .run(context -> assertThat(context.getBeansOfType(LlmProvider.class).values())
                        .extracting(LlmProvider::name)
                        .containsExactlyInAnyOrder("fake", "groq"));
    }

    @Test
    void selectingGroqWithoutAKeyFailsAtStartup() {
        runner.withPropertyValues("omnicare.llm.provider=groq", "omnicare.llm.groq.api-key=")
                .withUserConfiguration(LlmConfig.class, LlmProviderFactory.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void selectingGroqWithAKeyStartsCleanly() {
        runner.withPropertyValues("omnicare.llm.provider=groq", "omnicare.llm.groq.api-key=gsk_test")
                .withUserConfiguration(LlmConfig.class, LlmProviderFactory.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LlmProviderFactory.class).current().name())
                            .isEqualTo("groq");
                });
    }
}
