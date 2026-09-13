package com.omnicare.platform.conversation.infrastructure.persistence;

import com.omnicare.platform.conversation.domain.Message;
import com.omnicare.platform.conversation.domain.MessageRepository;
import com.omnicare.platform.shared.domain.ConversationId;
import java.util.List;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link MessageRepository}. */
@Repository
class JpaMessageRepository implements MessageRepository {

    private final MessageJpaRepository jpa;

    JpaMessageRepository(MessageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Message save(Message message) {
        jpa.save(ConversationMapper.toEntity(message));
        return message;
    }

    @Override
    public List<Message> findByConversation(ConversationId conversationId) {
        return jpa.findByConversationIdOrderByCreatedAtAsc(conversationId.value()).stream()
                .map(ConversationMapper::toDomain)
                .toList();
    }
}
