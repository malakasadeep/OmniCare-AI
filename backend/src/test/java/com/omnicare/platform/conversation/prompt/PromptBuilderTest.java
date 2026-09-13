package com.omnicare.platform.conversation.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnicare.platform.conversation.domain.Message;
import com.omnicare.platform.integration.llm.LlmMessage;
import com.omnicare.platform.integration.llm.LlmRole;
import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The sliding window is a cache eviction problem wearing a different hat: a
 * fixed budget, entries of varying size, and a policy for what to throw out.
 * These tests pin the policy down.
 */
class PromptBuilderTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final ConversationId CONVERSATION = ConversationId.generate();
    private static final Instant T0 = Instant.parse("2026-01-15T09:00:00Z");

    private static final String SYSTEM = "You are a support assistant. Reply in the customer's language.";

    private final TokenEstimator estimator = new TokenEstimator();

    private PromptBuilder builderWithBudget(int budget) {
        return new PromptBuilder(SYSTEM, budget, estimator);
    }

    /** Alternating user/assistant turns, oldest first, as the repository returns them. */
    private static List<Message> history(int count, int contentLength) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String content = ("m" + i + " ").repeat(Math.max(1, contentLength / 4)).trim();
            Instant at = T0.plusSeconds(i);
            messages.add(i % 2 == 0
                    ? Message.fromUser(TENANT, CONVERSATION, content, at)
                    : Message.fromAssistant(TENANT, CONVERSATION, content, at));
        }
        return messages;
    }

    @Nested
    class Shape {

        @Test
        void theSystemPromptIsAlwaysFirst() {
            List<LlmMessage> prompt = builderWithBudget(8000).build(List.of(), "hello");

            assertThat(prompt.get(0).role()).isEqualTo(LlmRole.SYSTEM);
            assertThat(prompt.get(0).content()).isEqualTo(SYSTEM);
        }

        @Test
        void theCurrentMessageIsAlwaysLast() {
            List<LlmMessage> prompt = builderWithBudget(8000).build(history(6, 20), "what about now?");

            LlmMessage last = prompt.get(prompt.size() - 1);
            assertThat(last.role()).isEqualTo(LlmRole.USER);
            assertThat(last.content()).isEqualTo("what about now?");
        }

        @Test
        void historySitsBetweenThemInTheOrderItHappened() {
            List<Message> history = history(4, 20);

            List<LlmMessage> prompt = builderWithBudget(8000).build(history, "next");

            List<String> middle = prompt.subList(1, prompt.size() - 1).stream()
                    .map(LlmMessage::content)
                    .toList();
            assertThat(middle).containsExactlyElementsOf(
                    history.stream().map(Message::content).toList());
        }

        @Test
        void domainRolesAreTranslatedToProviderRoles() {
            List<LlmMessage> prompt = builderWithBudget(8000).build(history(2, 10), "next");

            assertThat(prompt).extracting(LlmMessage::role)
                    .containsExactly(LlmRole.SYSTEM, LlmRole.USER, LlmRole.ASSISTANT, LlmRole.USER);
        }

        @Test
        void aConversationWithNoHistoryIsJustSystemAndQuestion() {
            List<LlmMessage> prompt = builderWithBudget(8000).build(List.of(), "first ever message");

            assertThat(prompt).hasSize(2);
        }
    }

    @Nested
    class Windowing {

        @Test
        void aLongConversationIsTrimmedToFitTheBudget() {
            List<LlmMessage> prompt = builderWithBudget(500).build(history(100, 60), "the latest question");

            assertThat(prompt.size()).isLessThan(102);
            assertThat(estimator.estimate(prompt)).isLessThanOrEqualTo(500);
        }

        @Test
        void aHundredMessagesDoNotBreakIt() {
            List<LlmMessage> prompt = builderWithBudget(8000).build(history(100, 60), "the latest question");

            assertThat(prompt.get(0).role()).isEqualTo(LlmRole.SYSTEM);
            assertThat(prompt.get(prompt.size() - 1).content()).isEqualTo("the latest question");
            assertThat(estimator.estimate(prompt)).isLessThanOrEqualTo(8000);
        }

        @Test
        void itIsTheOldestTurnsThatGoAndTheNewestThatStay() {
            List<Message> history = history(100, 60);

            List<LlmMessage> prompt = builderWithBudget(600).build(history, "latest");

            List<String> kept = prompt.stream().map(LlmMessage::content).toList();
            assertThat(kept).contains(history.get(99).content());
            assertThat(kept).doesNotContain(history.get(0).content());
        }

        @Test
        void theSystemPromptSurvivesEvenAWorthlesslySmallBudget() {
            List<LlmMessage> prompt = builderWithBudget(1).build(history(100, 60), "latest");

            assertThat(prompt.get(0).role()).isEqualTo(LlmRole.SYSTEM);
        }

        /**
         * Better to send an over-budget request the provider may reject with a
         * clear error than to silently answer a question the customer did not ask.
         */
        @Test
        void theCurrentQuestionIsNeverDroppedEvenIfItAloneExceedsTheBudget() {
            String enormous = "word ".repeat(5000);

            List<LlmMessage> prompt = builderWithBudget(100).build(history(10, 40), enormous);

            assertThat(prompt.get(prompt.size() - 1).content()).isEqualTo(enormous.strip());
            assertThat(prompt).hasSize(2);
        }

        @Test
        void aBudgetLargeEnoughKeepsEverything() {
            List<Message> history = history(10, 20);

            List<LlmMessage> prompt = builderWithBudget(100_000).build(history, "latest");

            assertThat(prompt).hasSize(history.size() + 2);
        }

        /**
         * An assistant turn whose question was evicted is an answer to nothing.
         * Left in, it reads to the model as an unprompted assertion of fact.
         */
        @Test
        void historyNeverBeginsWithAnAnswerWhoseQuestionWasDropped() {
            for (int budget = 120; budget <= 900; budget += 20) {
                List<LlmMessage> prompt = builderWithBudget(budget).build(history(100, 60), "latest");

                if (prompt.size() > 2) {
                    assertThat(prompt.get(1).role())
                            .as("first history entry at budget " + budget)
                            .isEqualTo(LlmRole.USER);
                }
            }
        }

        @Test
        void trimmingNeverReordersWhatItKeeps() {
            List<Message> history = history(100, 60);

            List<LlmMessage> prompt = builderWithBudget(700).build(history, "latest");

            List<String> kept = prompt.subList(1, prompt.size() - 1).stream()
                    .map(LlmMessage::content)
                    .toList();
            List<String> all = history.stream().map(Message::content).toList();

            assertThat(all.subList(all.size() - kept.size(), all.size())).containsExactlyElementsOf(kept);
        }
    }

    @Nested
    class SystemPromptContent {

        /** Multilingual support is a prompt feature, not a code feature. */
        @Test
        void instructsTheModelToAnswerInTheCustomersLanguage() {
            String shipped = SystemPrompt.current();

            assertThat(shipped.toLowerCase()).contains("language");
        }

        @Test
        void theShippedPromptIsNotEmpty() {
            assertThat(SystemPrompt.current()).isNotBlank();
        }

        @Test
        void theShippedPromptIsVersioned() {
            assertThat(SystemPrompt.version()).isNotBlank();
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsABlankCurrentMessage() {
            assertThatThrownBy(() -> builderWithBudget(8000).build(List.of(), "   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsANonPositiveBudget() {
            assertThatThrownBy(() -> new PromptBuilder(SYSTEM, 0, estimator))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsABlankSystemPrompt() {
            assertThatThrownBy(() -> new PromptBuilder("  ", 8000, estimator))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
