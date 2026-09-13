package com.omnicare.platform.conversation.domain;

import static com.omnicare.platform.conversation.domain.ConversationStatus.AWAITING_HUMAN;
import static com.omnicare.platform.conversation.domain.ConversationStatus.BOT_ACTIVE;
import static com.omnicare.platform.conversation.domain.ConversationStatus.HUMAN_ACTIVE;
import static com.omnicare.platform.conversation.domain.ConversationStatus.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ConversationStatusTest {

    record Transition(ConversationStatus from, ConversationStatus to) {}

    /** The complete machine, written out so the test is the specification. */
    private static final Set<Transition> LEGAL = Set.of(
            new Transition(BOT_ACTIVE, AWAITING_HUMAN),
            new Transition(BOT_ACTIVE, RESOLVED),
            new Transition(AWAITING_HUMAN, HUMAN_ACTIVE),
            new Transition(AWAITING_HUMAN, RESOLVED),
            new Transition(HUMAN_ACTIVE, BOT_ACTIVE),
            new Transition(HUMAN_ACTIVE, RESOLVED));

    static Stream<Transition> legalTransitions() {
        return LEGAL.stream();
    }

    static Stream<Transition> illegalTransitions() {
        Set<Transition> everyPair = new HashSet<>();
        for (ConversationStatus from : ConversationStatus.values()) {
            for (ConversationStatus to : ConversationStatus.values()) {
                everyPair.add(new Transition(from, to));
            }
        }
        everyPair.removeAll(LEGAL);
        return everyPair.stream();
    }

    @ParameterizedTest(name = "{0} is allowed")
    @MethodSource("legalTransitions")
    void allowsEveryLegalTransition(Transition t) {
        assertThat(t.from().canTransitionTo(t.to())).isTrue();
    }

    @ParameterizedTest(name = "{0} is rejected")
    @MethodSource("illegalTransitions")
    void rejectsEveryOtherTransition(Transition t) {
        assertThat(t.from().canTransitionTo(t.to())).isFalse();
    }

    @Test
    void canTransitionToRejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> BOT_ACTIVE.canTransitionTo(null));
    }

    @Test
    void resolvedIsTerminal() {
        assertThat(RESOLVED.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ConversationStatus.class, names = "RESOLVED", mode = EnumSource.Mode.EXCLUDE)
    void everyOtherStatusIsNotTerminal(ConversationStatus status) {
        assertThat(status.isTerminal()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ConversationStatus.class, names = "RESOLVED", mode = EnumSource.Mode.EXCLUDE)
    void everyNonTerminalStatusCanReachResolved(ConversationStatus status) {
        assertThat(status.canTransitionTo(RESOLVED)).isTrue();
    }

    @Test
    void aStatusCannotTransitionToItself() {
        for (ConversationStatus status : ConversationStatus.values()) {
            assertThat(status.canTransitionTo(status))
                    .as("%s -> %s", status, status)
                    .isFalse();
        }
    }
}
