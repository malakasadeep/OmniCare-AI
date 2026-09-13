package com.omnicare.platform.conversation.prompt;

import com.omnicare.platform.conversation.domain.Message;
import com.omnicare.platform.conversation.domain.MessageRole;
import com.omnicare.platform.integration.llm.LlmMessage;
import com.omnicare.platform.integration.llm.LlmRole;
import com.omnicare.platform.shared.domain.Guards;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Assembles the prompt: system instructions, as much recent history as fits, and
 * the question being asked.
 *
 * <p>The interesting part is what happens when a conversation outgrows the
 * budget, which is a cache eviction problem in different clothes — a fixed
 * capacity, entries of varying size, and a policy deciding what to throw out.
 * The policy here is least-recently-used in its simplest form: the oldest turns
 * go first, because in support the last few exchanges carry nearly all the
 * meaning and the opening pleasantries carry none.
 *
 * <p>Two entries are pinned and never evicted. The system prompt, because
 * without it the assistant stops being this product's assistant. And the current
 * question, because dropping it would mean answering something the customer did
 * not ask — an over-budget request the provider rejects with a clear error is
 * strictly better than a confident answer to the wrong question.
 *
 * <p>The alternative policy is to summarise evicted turns rather than discard
 * them, which keeps older context at the price of an extra model call per
 * eviction and a summary that can itself be wrong. Recorded in
 * {@code docs/backlog.md}; the window is the right first move.
 */
public class PromptBuilder {

    private final String systemPrompt;
    private final int tokenBudget;
    private final TokenEstimator estimator;

    public PromptBuilder(String systemPrompt, int tokenBudget, TokenEstimator estimator) {
        this.systemPrompt = Guards.requireNonBlank(systemPrompt, "systemPrompt");
        if (tokenBudget <= 0) {
            throw new IllegalArgumentException("tokenBudget must be positive, was " + tokenBudget);
        }
        this.tokenBudget = tokenBudget;
        this.estimator = Objects.requireNonNull(estimator, "estimator must not be null");
    }

    /**
     * @param history        the transcript so far, oldest first
     * @param currentMessage what the customer just asked
     * @return system prompt, surviving history in order, then the question
     */
    public List<LlmMessage> build(List<Message> history, String currentMessage) {
        Objects.requireNonNull(history, "history must not be null");

        LlmMessage system = LlmMessage.system(systemPrompt);
        LlmMessage question = LlmMessage.user(Guards.requireNonBlank(currentMessage, "currentMessage"));

        int remaining = tokenBudget - estimator.estimate(system) - estimator.estimate(question);

        // Newest first: spend what is left on the most recent turns.
        Deque<LlmMessage> kept = new ArrayDeque<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            LlmMessage candidate = toLlmMessage(history.get(i));
            int cost = estimator.estimate(candidate);
            if (cost > remaining) {
                break;
            }
            remaining -= cost;
            kept.addFirst(candidate);
        }

        dropLeadingOrphanedAnswer(kept);

        List<LlmMessage> prompt = new ArrayList<>(kept.size() + 2);
        prompt.add(system);
        prompt.addAll(kept);
        prompt.add(question);
        return List.copyOf(prompt);
    }

    /**
     * Removes a surviving assistant turn whose question was evicted.
     *
     * <p>Such a turn is an answer to a question the model cannot see, so it
     * reads as an unprompted assertion of fact — exactly the kind of thing a
     * model will then defend or build on.
     */
    private static void dropLeadingOrphanedAnswer(Deque<LlmMessage> kept) {
        while (!kept.isEmpty() && kept.peekFirst().role() == LlmRole.ASSISTANT) {
            kept.removeFirst();
        }
    }

    private static LlmMessage toLlmMessage(Message message) {
        return new LlmMessage(toLlmRole(message.role()), message.content());
    }

    /**
     * Translates a stored transcript role into a provider role.
     *
     * <p>{@code TOOL} becomes {@code ASSISTANT} for now: Week 4 introduces tool
     * turns, and until the providers' tool-message format is wired up, a tool
     * result is closer to something the assistant established than to something
     * the customer said.
     */
    private static LlmRole toLlmRole(MessageRole role) {
        return switch (role) {
            case SYSTEM -> LlmRole.SYSTEM;
            case USER -> LlmRole.USER;
            case ASSISTANT, TOOL -> LlmRole.ASSISTANT;
        };
    }
}
