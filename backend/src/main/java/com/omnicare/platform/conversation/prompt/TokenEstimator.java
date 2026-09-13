package com.omnicare.platform.conversation.prompt;

import com.omnicare.platform.integration.llm.LlmMessage;
import java.util.List;

/**
 * Estimates how many tokens a prompt will cost.
 *
 * <p>An estimate on purpose. A token is a piece of a word chosen by the model's
 * own tokeniser — "unhappiness" may be three tokens, a rare name five — and the
 * only exact count comes from that tokeniser, which differs per model and would
 * mean shipping vocabulary files for each. Roughly four characters per token
 * holds well enough for English prose and is the number every provider quotes.
 *
 * <p>Because it is approximate, the budget it feeds must be set below the
 * model's real context window, not at it. Non-Latin scripts cost more tokens per
 * character than this assumes, so an estimate is most likely to run low exactly
 * where the product promises to work — see {@link #CHARS_PER_TOKEN}.
 */
public class TokenEstimator {

    /**
     * The usual rule of thumb for English. Languages written without spaces, and
     * scripts outside Latin-1, tokenise far less efficiently — sometimes a token
     * per character — so this under-counts for them, which is why callers leave
     * headroom rather than budgeting to the limit.
     */
    static final int CHARS_PER_TOKEN = 4;

    /**
     * Per-message overhead. Providers wrap each message in role markers and
     * separators that are billed but are not part of the content.
     */
    static final int MESSAGE_OVERHEAD_TOKENS = 4;

    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.ceilDiv(text.length(), CHARS_PER_TOKEN);
    }

    public int estimate(LlmMessage message) {
        return estimate(message.content()) + MESSAGE_OVERHEAD_TOKENS;
    }

    public int estimate(List<LlmMessage> messages) {
        return messages.stream().mapToInt(this::estimate).sum();
    }
}
