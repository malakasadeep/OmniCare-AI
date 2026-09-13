package com.omnicare.platform.conversation.infrastructure.persistence;

import com.omnicare.platform.conversation.domain.Conversation;
import com.omnicare.platform.conversation.domain.Message;
import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.MessageId;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import com.omnicare.platform.shared.domain.VisitorId;
import java.util.UUID;

/** Translates between the conversation domain classes and their rows. */
final class ConversationMapper {

    private ConversationMapper() {
    }

    static ConversationEntity toEntity(Conversation conversation) {
        return new ConversationEntity(
                conversation.id().value(),
                conversation.tenantId().value(),
                conversation.visitorId().value(),
                conversation.status(),
                conversation.assignedOperator().map(UserId::value).orElse(null),
                conversation.escalationReason().orElse(null),
                conversation.createdAt(),
                conversation.updatedAt());
    }

    static Conversation toDomain(ConversationEntity entity) {
        UUID operatorId = entity.getAssignedOperatorId();
        return Conversation.rehydrate(
                new ConversationId(entity.getId()),
                new TenantId(entity.getTenantId()),
                new VisitorId(entity.getVisitorId()),
                entity.getStatus(),
                operatorId == null ? null : new UserId(operatorId),
                entity.getEscalationReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static MessageEntity toEntity(Message message) {
        return new MessageEntity(
                message.id().value(),
                message.tenantId().value(),
                message.conversationId().value(),
                message.role(),
                message.content(),
                message.createdAt());
    }

    static Message toDomain(MessageEntity entity) {
        return Message.rehydrate(
                new MessageId(entity.getId()),
                new TenantId(entity.getTenantId()),
                new ConversationId(entity.getConversationId()),
                entity.getRole(),
                entity.getContent(),
                entity.getCreatedAt());
    }
}
