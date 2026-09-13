package com.omnicare.platform.conversation.infrastructure.persistence;

import com.omnicare.platform.conversation.domain.MessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The {@code messages} row. See {@link ConversationEntity}. */
@Entity
@Table(name = "messages")
class MessageEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MessageEntity() {
    }

    MessageEntity(UUID id, UUID tenantId, UUID conversationId, MessageRole role,
                  String content, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getConversationId() {
        return conversationId;
    }

    MessageRole getRole() {
        return role;
    }

    String getContent() {
        return content;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
