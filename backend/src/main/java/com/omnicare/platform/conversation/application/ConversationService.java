package com.omnicare.platform.conversation.application;

import com.omnicare.platform.conversation.domain.Conversation;
import com.omnicare.platform.conversation.domain.ConversationRepository;
import com.omnicare.platform.conversation.domain.Message;
import com.omnicare.platform.conversation.domain.MessageRepository;
import com.omnicare.platform.shared.api.ResourceNotFoundException;
import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.VisitorId;
import com.omnicare.platform.shared.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversation use cases: start one, add a turn, read the transcript.
 *
 * <p>Holds no business rules — those live in {@code Conversation} — only the
 * sequencing of repository calls that a use case needs.
 */
@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final Replier replier;
    private final java.time.Clock clock;

    ConversationService(ConversationRepository conversations,
                        MessageRepository messages,
                        Replier replier,
                        java.time.Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.replier = replier;
        this.clock = clock;
    }

    @Transactional
    public Conversation start(VisitorId visitorId) {
        return conversations.save(Conversation.start(currentTenant(), visitorId, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<Conversation> list(int page, int size) {
        return conversations.findRecentForTenant(currentTenant(), page, size);
    }

    @Transactional(readOnly = true)
    public long count() {
        return conversations.countForTenant(currentTenant());
    }

    @Transactional(readOnly = true)
    public List<Message> transcript(ConversationId conversationId) {
        requireReachable(conversationId);
        return messages.findByConversation(conversationId);
    }

    /**
     * Records the visitor's turn and answers it.
     *
     * @return the reply, which is what the caller is waiting for
     */
    @Transactional
    public Message postMessage(ConversationId conversationId, String content) {
        Conversation conversation = requireReachable(conversationId);
        TenantId tenant = conversation.tenantId();

        // Read the transcript before adding this turn: the builder takes the
        // history and the current question as separate arguments.
        List<Message> history = messages.findByConversation(conversationId);

        Instant askedAt = clock.instant();
        messages.save(Message.fromUser(tenant, conversationId, content, askedAt));

        // Strictly after the question rather than a second clock reading.
        // Postgres keeps microseconds, and two reads inside one request can land
        // in the same one — which would make the transcript's order, and so the
        // order the model is later shown, undefined.
        Instant answeredAt = askedAt.plusMillis(1);
        return messages.save(Message.fromAssistant(
                tenant, conversationId, replier.replyTo(history, content), answeredAt));
    }

    private Conversation requireReachable(ConversationId conversationId) {
        // No tenant check here by design: row level security has already made
        // another tenant's conversation unreachable, so "not yours" and "not
        // there" are the same empty Optional.
        return conversations.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation"));
    }

    private static TenantId currentTenant() {
        return TenantContext.currentTenant().orElseThrow(() -> new IllegalStateException(
                "No tenant in context; this use case requires an authenticated request"));
    }
}
