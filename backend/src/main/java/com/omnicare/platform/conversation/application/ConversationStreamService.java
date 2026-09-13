package com.omnicare.platform.conversation.application;

import com.omnicare.platform.conversation.domain.Conversation;
import com.omnicare.platform.conversation.domain.ConversationRepository;
import com.omnicare.platform.conversation.domain.Message;
import com.omnicare.platform.conversation.domain.MessageRepository;
import com.omnicare.platform.shared.api.ResourceNotFoundException;
import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

/**
 * The streaming half of a conversation turn.
 *
 * <p>Three things here are easy to get wrong and are the reason this is not
 * simply a method on {@code ConversationService}.
 *
 * <p><b>No transaction spans the stream.</b> The visitor's message is committed
 * before a single token is produced, and the reply is committed after the last
 * one. A model can take tens of seconds; a transaction held open across that
 * pins a pooled connection and holds its locks for the whole time, and a handful
 * of concurrent visitors would exhaust the pool.
 *
 * <p><b>The tenant has to be re-established.</b> {@link TenantContext} is a
 * {@link ThreadLocal} set by a servlet filter on the request thread, but the
 * terminal callback runs on whichever thread the stream finished on. Without
 * restoring it the final write has no tenant, and row level security rejects it
 * — the reply would simply vanish.
 *
 * <p><b>An abandoned stream still has to be saved.</b> If the visitor closes the
 * tab, {@code doFinally} sees a cancel and the partial answer is persisted, so
 * the transcript matches what they actually saw.
 */
@Service
public class ConversationStreamService {

    private static final Logger log = LoggerFactory.getLogger(ConversationStreamService.class);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final Replier replier;
    private final TransactionTemplate transactions;
    private final Clock clock;

    ConversationStreamService(ConversationRepository conversations,
                              MessageRepository messages,
                              Replier replier,
                              PlatformTransactionManager transactionManager,
                              Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.replier = replier;
        // An explicit template rather than @Transactional: the transactions here
        // are opened and closed around the stream, not wrapped around a method.
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Flux<String> stream(ConversationId conversationId, String visitorMessage) {
        // Runs on the request thread, where the tenant context still exists, and
        // commits before any token is generated.
        Turn turn = transactions.execute(status -> {
            Conversation conversation = conversations.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation"));
            Instant askedAt = clock.instant();
            messages.save(Message.fromUser(
                    conversation.tenantId(), conversationId, visitorMessage, askedAt));
            return new Turn(conversation.tenantId(), conversationId, askedAt);
        });

        StringBuilder answer = new StringBuilder();
        AtomicBoolean saved = new AtomicBoolean(false);

        return replier.streamReplyTo(visitorMessage)
                .doOnNext(answer::append)
                .doFinally(signal -> persistReply(turn, answer.toString(), saved, signal.toString()));
    }

    /**
     * Writes whatever was produced, once, whether the stream completed, errored
     * or was cancelled.
     */
    private void persistReply(Turn turn, String answer, AtomicBoolean saved, String signal) {
        if (answer.isBlank() || !saved.compareAndSet(false, true)) {
            return;
        }
        // Restore the tenant: this runs on a stream thread, not the request one.
        TenantContext.set(turn.tenantId());
        try {
            transactions.executeWithoutResult(status -> messages.save(Message.fromAssistant(
                    turn.tenantId(),
                    turn.conversationId(),
                    answer,
                    turn.askedAt().plusMillis(1))));
        } catch (RuntimeException e) {
            // The visitor has already read the answer; failing the response now
            // would help nobody, so this is logged and swallowed.
            log.error("Could not persist streamed reply for conversation {}",
                    turn.conversationId().value(), e);
        } finally {
            TenantContext.clear();
            log.debug("Stream for conversation {} ended with {} after {} characters",
                    turn.conversationId().value(), signal, answer.length());
        }
    }

    /** What the terminal callback needs, captured while the request thread still has it. */
    private record Turn(TenantId tenantId, ConversationId conversationId, Instant askedAt) {
    }
}
