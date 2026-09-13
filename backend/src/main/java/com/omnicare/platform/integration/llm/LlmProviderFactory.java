package com.omnicare.platform.integration.llm;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Chooses which {@link LlmProvider} the application uses — the Factory over the
 * Strategy.
 *
 * <p>It is handed every provider on the classpath rather than constructing them
 * from a {@code switch}. Adding a provider therefore needs no edit here: declare
 * the bean, name it, and set one configuration value. That is the Open/Closed
 * Principle with something to show for it.
 *
 * <p>An unknown name fails at startup, not on the first visitor's message.
 */
@Component
public class LlmProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderFactory.class);

    private final Map<String, LlmProvider> byName;
    private final LlmProvider selected;

    LlmProviderFactory(List<LlmProvider> providers, LlmProperties properties) {
        this.byName = providers.stream().collect(Collectors.toMap(
                provider -> provider.name().toLowerCase(Locale.ROOT), Function.identity()));

        String configured = properties.provider() == null
                ? "" : properties.provider().toLowerCase(Locale.ROOT);
        this.selected = byName.get(configured);
        if (selected == null) {
            throw new IllegalStateException(
                    "omnicare.llm.provider is '%s'; known providers are %s".formatted(
                            properties.provider(), byName.keySet().stream().sorted().toList()));
        }
        log.info("Language model provider: {}", selected.name());
    }

    public LlmProvider current() {
        return selected;
    }
}
