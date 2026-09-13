package com.omnicare.platform.conversation.domain;

import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.Guards;
import com.omnicare.platform.shared.domain.MessageId;
import com.omnicare.platform.shared.domain.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * One turn in a conversation. Immutable once created: a message that has been
 * said cannot be unsaid, and the transcript is what the model is later shown.
 *
 * <p>The role is never passed in directly — the named factories are the whole
 * API, so there is no way to construct a message whose role does not match the
 * intent at the call site.
 */
public final class Message {

    private final MessageId id;
    private final TenantId tenantId;
    private final ConversationId conversationId;
    private final MessageRole role;
    private final String content;
    private final Instant createdAt;

    private Message(MessageId id,
                    TenantId tenantId,
                    ConversationId conversationId,
                    MessageRole role,
                    String content,
                    Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Message fromUser(TenantId tenantId,
                                   ConversationId conversationId,
                                   String content,
                                   Instant createdAt) {
        return create(tenantId, conversationId, MessageRole.USER, content, createdAt);
    }

    public static Message fromAssistant(TenantId tenantId,
                                        ConversationId conversationId,
                                        String content,
                                        Instant createdAt) {
        return create(tenantId, conversationId, MessageRole.ASSISTANT, content, createdAt);
    }

    public static Message system(TenantId tenantId,
                                 ConversationId conversationId,
                                 String content,
                                 Instant createdAt) {
        return create(tenantId, conversationId, MessageRole.SYSTEM, content, createdAt);
    }

    private static Message create(TenantId tenantId,
                                  ConversationId conversationId,
                                  MessageRole role,
                                  String content,
                                  Instant createdAt) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        return new Message(
                MessageId.generate(),
                tenantId,
                conversationId,
                role,
                Guards.requireNonBlank(content, "content"),
                createdAt);
    }

    public MessageId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public ConversationId conversationId() {
        return conversationId;
    }

    public MessageRole role() {
        return role;
    }

    public String content() {
        return content;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Message other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Message[id=%s, conversation=%s, role=%s, length=%d]".formatted(
                id.value(), conversationId.value(), role, content.length());
    }
}
