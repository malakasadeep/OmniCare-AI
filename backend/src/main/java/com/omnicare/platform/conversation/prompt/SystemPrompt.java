package com.omnicare.platform.conversation.prompt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/**
 * The system prompt that ships with this build.
 *
 * <p>Loaded from a versioned resource rather than written as a Java string
 * literal. A prompt is a behavioural artefact: it wants to be diffable on its
 * own, reviewable by someone who does not read Java, and rollable back without
 * recompiling a class. See {@code docs/prompts/README.md}.
 */
public final class SystemPrompt {

    /** Bump this to promote a new version; never edit a published file in place. */
    private static final String CURRENT_VERSION = "v1";

    private static final String TEXT = load(CURRENT_VERSION);

    private SystemPrompt() {
    }

    public static String current() {
        return TEXT;
    }

    public static String version() {
        return CURRENT_VERSION;
    }

    private static String load(String version) {
        ClassPathResource resource = new ClassPathResource("prompts/system-%s.txt".formatted(version));
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            // Unrecoverable and worth failing startup for: an assistant running
            // without its system prompt is not a degraded assistant, it is a
            // different product.
            throw new UncheckedIOException(
                    "Missing system prompt resource for version " + version, e);
        }
    }
}
