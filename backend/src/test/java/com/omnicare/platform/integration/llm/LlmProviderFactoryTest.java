package com.omnicare.platform.integration.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class LlmProviderFactoryTest {

    /** A second provider, so "picks the configured one" means something. */
    private static final class StubProvider implements LlmProvider {

        private final String name;

        StubProvider(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            return new LlmResponse("from " + name, 0, 0, "stop");
        }

        @Override
        public Flux<String> streamChat(LlmRequest request) {
            return Flux.just("from ", name);
        }
    }

    private static LlmProperties configuredFor(String provider) {
        return new LlmProperties(provider, "some-model", 0.3, 256, Duration.ofSeconds(30),
                new LlmProperties.Groq("https://api.groq.test/openai/v1", "key"));
    }

    private static final List<LlmProvider> AVAILABLE =
            List.of(new StubProvider("fake"), new StubProvider("groq"));

    @Test
    void selectsTheProviderNamedInConfiguration() {
        LlmProviderFactory factory = new LlmProviderFactory(AVAILABLE, configuredFor("groq"));

        assertThat(factory.current().name()).isEqualTo("groq");
    }

    @Test
    void changingOneConfigurationValueSwitchesProvider() {
        assertThat(new LlmProviderFactory(AVAILABLE, configuredFor("groq")).current().name())
                .isEqualTo("groq");
        assertThat(new LlmProviderFactory(AVAILABLE, configuredFor("fake")).current().name())
                .isEqualTo("fake");
    }

    @Test
    void selectionIsCaseInsensitive() {
        LlmProviderFactory factory = new LlmProviderFactory(AVAILABLE, configuredFor("GROQ"));

        assertThat(factory.current().name()).isEqualTo("groq");
    }

    @Test
    void anUnknownProviderFailsAtConstructionRatherThanOnFirstUse() {
        assertThatThrownBy(() -> new LlmProviderFactory(AVAILABLE, configuredFor("nope")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void theFailureListsWhatWasAvailableSoTheMistakeIsObvious() {
        assertThatThrownBy(() -> new LlmProviderFactory(AVAILABLE, configuredFor("nope")))
                .hasMessageContaining("fake")
                .hasMessageContaining("groq");
    }

    @Test
    void aMissingProviderSettingIsAlsoAStartupFailure() {
        assertThatThrownBy(() -> new LlmProviderFactory(AVAILABLE, configuredFor(null)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * The selected provider is not configured as a Groq provider or a fake
     * provider — only as an {@link LlmProvider}. If this compiled against a
     * vendor type the abstraction would already have leaked.
     */
    @Test
    void callersReceiveOnlyTheNeutralInterface() {
        LlmProvider provider = new LlmProviderFactory(AVAILABLE, configuredFor("fake")).current();

        LlmResponse response = provider.chat(new LlmRequest(
                List.of(LlmMessage.user("hi")), "some-model", 0.3, 128));

        assertThat(response.content()).isEqualTo("from fake");
    }
}
