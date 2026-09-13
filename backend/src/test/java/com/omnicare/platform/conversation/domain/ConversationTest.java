package com.omnicare.platform.conversation.domain;

import static com.omnicare.platform.conversation.domain.ConversationStatus.BOT_ACTIVE;
import static com.omnicare.platform.conversation.domain.ConversationStatus.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnicare.platform.shared.domain.IllegalConversationStateException;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import com.omnicare.platform.shared.domain.VisitorId;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConversationTest {

    private static final Instant T0 = Instant.parse("2026-01-15T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-15T09:05:00Z");
    private static final Instant T2 = Instant.parse("2026-01-15T09:10:00Z");
    private static final Instant T3 = Instant.parse("2026-01-15T09:15:00Z");

    private static final TenantId TENANT = TenantId.generate();
    private static final VisitorId VISITOR = VisitorId.generate();
    private static final UserId OPERATOR = UserId.generate();

    private static Conversation newConversation() {
        return Conversation.start(TENANT, VISITOR, T0);
    }

    private static Conversation awaitingHuman() {
        Conversation c = newConversation();
        c.escalateToHuman("customer asked for a person", T1);
        return c;
    }

    private static Conversation humanActive() {
        Conversation c = awaitingHuman();
        c.assignOperator(OPERATOR, T2);
        return c;
    }

    private static Conversation resolved() {
        Conversation c = newConversation();
        c.resolve(T1);
        return c;
    }

    @Nested
    class Start {

        @Test
        void beginsInBotActiveWithMatchingTimestampsAndNoOperatorOrReason() {
            Conversation c = Conversation.start(TENANT, VISITOR, T0);

            assertThat(c.status()).isEqualTo(BOT_ACTIVE);
            assertThat(c.tenantId()).isEqualTo(TENANT);
            assertThat(c.visitorId()).isEqualTo(VISITOR);
            assertThat(c.id()).isNotNull();
            assertThat(c.createdAt()).isEqualTo(T0);
            assertThat(c.updatedAt()).isEqualTo(T0);
            assertThat(c.assignedOperator()).isEmpty();
            assertThat(c.escalationReason()).isEmpty();
        }

        @Test
        void mintsADistinctIdentityEachTime() {
            assertThat(Conversation.start(TENANT, VISITOR, T0).id())
                    .isNotEqualTo(Conversation.start(TENANT, VISITOR, T0).id());
        }

        @Test
        void rejectsNullTenant() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Conversation.start(null, VISITOR, T0));
        }

        @Test
        void rejectsNullVisitor() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Conversation.start(TENANT, null, T0));
        }

        @Test
        void rejectsNullStartedAt() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Conversation.start(TENANT, VISITOR, null));
        }
    }

    @Nested
    class LegalTransitions {

        @Test
        void escalateMovesToAwaitingHumanAndRecordsTheReason() {
            Conversation c = newConversation();

            c.escalateToHuman("customer is upset", T1);

            assertThat(c.status()).isEqualTo(ConversationStatus.AWAITING_HUMAN);
            assertThat(c.escalationReason()).contains("customer is upset");
            assertThat(c.createdAt()).isEqualTo(T0);
            assertThat(c.updatedAt()).isEqualTo(T1);
        }

        @Test
        void assignOperatorMovesToHumanActiveAndRecordsTheOperator() {
            Conversation c = awaitingHuman();

            c.assignOperator(OPERATOR, T2);

            assertThat(c.status()).isEqualTo(ConversationStatus.HUMAN_ACTIVE);
            assertThat(c.assignedOperator()).contains(OPERATOR);
            assertThat(c.updatedAt()).isEqualTo(T2);
        }

        @Test
        void returnToBotMovesBackToBotActiveAndClearsTheOperator() {
            Conversation c = humanActive();

            c.returnToBot(T3);

            assertThat(c.status()).isEqualTo(BOT_ACTIVE);
            assertThat(c.assignedOperator()).isEmpty();
            assertThat(c.updatedAt()).isEqualTo(T3);
        }

        @Test
        void resolveFromBotActive() {
            Conversation c = newConversation();

            c.resolve(T1);

            assertThat(c.status()).isEqualTo(RESOLVED);
            assertThat(c.updatedAt()).isEqualTo(T1);
        }

        @Test
        void resolveFromAwaitingHuman() {
            Conversation c = awaitingHuman();

            c.resolve(T2);

            assertThat(c.status()).isEqualTo(RESOLVED);
        }

        @Test
        void resolveFromHumanActive() {
            Conversation c = humanActive();

            c.resolve(T3);

            assertThat(c.status()).isEqualTo(RESOLVED);
        }
    }

    @Nested
    class IllegalTransitions {

        @Test
        void cannotEscalateWhenAlreadyAwaitingHuman() {
            assertThatThrownBy(() -> awaitingHuman().escalateToHuman("again", T2))
                    .isInstanceOf(IllegalConversationStateException.class);
        }

        @Test
        void cannotEscalateWhenHumanActive() {
            assertThatThrownBy(() -> humanActive().escalateToHuman("again", T3))
                    .isInstanceOf(IllegalConversationStateException.class);
        }

        @Test
        void cannotEscalateWhenResolved() {
            assertThatThrownBy(() -> resolved().escalateToHuman("again", T2))
                    .isInstanceOf(IllegalConversationStateException.class);
        }

        @Test
        void cannotAssignOperatorWhenBotActive() {
            assertThatThrownBy(() -> newConversation().assignOperator(OPERATOR, T1))
                    .isInstanceOf(IllegalConversationStateException.class);
        }

        @Test
        void cannotAssignOperatorWhenAlreadyHumanActive() {
            assertThatThrownBy(() -> humanActive().assignOperator(OPERATOR, T3))
                    .isInstanceOf(IllegalConversationStateException.class);
        }

        @Test
        void cannotAssignOperatorWhenResolved() {
            assertThatThrownBy(() -> resolved().assignOperator(OPERATOR, T2))
                    .isInstanceOf(IllegalConversationStateException.class);
        }

        @Test
        void cannotReturnToBotWhenBotActive() {
            assertThatThrownBy(() -> newConversation().returnToBot(T1))
                    .isInstanceOf(IllegalConversationStateException.class);
        }

        @Test
        void cannotReturnToBotWhenAwaitingHuman() {
            assertThatThrownBy(() -> awaitingHuman().returnToBot(T2))
                    .isInstanceOf(IllegalConversationStateException.class);
        }

        @Test
        void cannotReturnToBotWhenResolved() {
            assertThatThrownBy(() -> resolved().returnToBot(T2))
                    .isInstanceOf(IllegalConversationStateException.class);
        }

        @Test
        void cannotResolveTwice() {
            Conversation c = resolved();

            assertThatThrownBy(() -> c.resolve(T2))
                    .isInstanceOf(IllegalConversationStateException.class);
        }
    }

    @Nested
    class ExceptionPayloadAndInvariants {

        @Test
        void illegalTransitionCarriesIdCurrentAndAttemptedTarget() {
            Conversation c = humanActive();

            assertThatThrownBy(() -> c.escalateToHuman("too late", T3))
                    .isInstanceOfSatisfying(IllegalConversationStateException.class, ex -> {
                        assertThat(ex.getConversationId()).isEqualTo(c.id());
                        assertThat(ex.getCurrentStatus()).isEqualTo("HUMAN_ACTIVE");
                        assertThat(ex.getAttemptedStatus()).isEqualTo("AWAITING_HUMAN");
                    });
        }

        @Test
        void aRejectedTransitionLeavesTheEntityUntouched() {
            Conversation c = newConversation();
            c.resolve(T1);

            assertThatThrownBy(() -> c.escalateToHuman("too late", T2))
                    .isInstanceOf(IllegalConversationStateException.class);

            assertThat(c.status()).isEqualTo(RESOLVED);
            assertThat(c.escalationReason()).isEmpty();
            assertThat(c.updatedAt()).isEqualTo(T1);
        }
    }

    @Nested
    class ArgumentValidation {

        @Test
        void escalateRejectsNullReason() {
            assertThatNullPointerException()
                    .isThrownBy(() -> newConversation().escalateToHuman(null, T1));
        }

        @Test
        void escalateRejectsBlankReason() {
            assertThatThrownBy(() -> newConversation().escalateToHuman("   ", T1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void escalateRejectsNullTimestamp() {
            assertThatNullPointerException()
                    .isThrownBy(() -> newConversation().escalateToHuman("reason", null));
        }

        @Test
        void assignOperatorRejectsNullOperator() {
            Conversation c = awaitingHuman();
            assertThatNullPointerException()
                    .isThrownBy(() -> c.assignOperator(null, T2));
        }

        @Test
        void assignOperatorRejectsNullTimestamp() {
            Conversation c = awaitingHuman();
            assertThatNullPointerException()
                    .isThrownBy(() -> c.assignOperator(OPERATOR, null));
        }

        @Test
        void returnToBotRejectsNullTimestamp() {
            Conversation c = humanActive();
            assertThatNullPointerException()
                    .isThrownBy(() -> c.returnToBot(null));
        }

        @Test
        void resolveRejectsNullTimestamp() {
            assertThatNullPointerException()
                    .isThrownBy(() -> newConversation().resolve(null));
        }
    }

    @Nested
    class Identity {

        @Test
        void equalsItself() {
            Conversation c = newConversation();
            assertThat(c).isEqualTo(c).hasSameHashCodeAs(c);
        }

        @Test
        void twoStartedConversationsAreNotEqual() {
            assertThat(newConversation()).isNotEqualTo(newConversation());
        }

        @Test
        void notEqualToNullOrAnotherType() {
            Conversation c = newConversation();
            assertThat(c).isNotEqualTo(null);
            assertThat(c.equals("conversation")).isFalse();
        }
    }
}
