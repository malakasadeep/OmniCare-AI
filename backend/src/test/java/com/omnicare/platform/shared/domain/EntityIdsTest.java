package com.omnicare.platform.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The six entity id records are structurally identical, so their shared contract
 * is exercised once here, parameterised over every type:
 * <ul>
 *   <li>the canonical constructor rejects a null {@link UUID};</li>
 *   <li>{@code generate()} mints a fresh, non-null id on every call;</li>
 *   <li>two ids wrapping the same {@link UUID} are equal (value semantics).</li>
 * </ul>
 */
class EntityIdsTest {

    static Stream<Arguments> idTypes() {
        return Stream.of(
                arguments("TenantId",
                        (Function<UUID, Object>) TenantId::new, (Supplier<Object>) TenantId::generate),
                arguments("UserId",
                        (Function<UUID, Object>) UserId::new, (Supplier<Object>) UserId::generate),
                arguments("ConversationId",
                        (Function<UUID, Object>) ConversationId::new, (Supplier<Object>) ConversationId::generate),
                arguments("VisitorId",
                        (Function<UUID, Object>) VisitorId::new, (Supplier<Object>) VisitorId::generate),
                arguments("DocumentId",
                        (Function<UUID, Object>) DocumentId::new, (Supplier<Object>) DocumentId::generate),
                arguments("MessageId",
                        (Function<UUID, Object>) MessageId::new, (Supplier<Object>) MessageId::generate));
    }

    @ParameterizedTest(name = "{0} rejects a null UUID")
    @MethodSource("idTypes")
    void rejectsNullUuid(String name, Function<UUID, Object> fromUuid, Supplier<Object> generate) {
        assertThatNullPointerException()
                .isThrownBy(() -> fromUuid.apply(null));
    }

    @ParameterizedTest(name = "{0}.generate() returns a fresh, non-null id each call")
    @MethodSource("idTypes")
    void generateProducesFreshNonNullIds(String name, Function<UUID, Object> fromUuid, Supplier<Object> generate) {
        Object first = generate.get();
        Object second = generate.get();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @ParameterizedTest(name = "{0} has value semantics")
    @MethodSource("idTypes")
    void hasValueSemantics(String name, Function<UUID, Object> fromUuid, Supplier<Object> generate) {
        UUID raw = UUID.randomUUID();

        assertThat(fromUuid.apply(raw))
                .isEqualTo(fromUuid.apply(raw))
                .hasSameHashCodeAs(fromUuid.apply(raw));
    }
}
