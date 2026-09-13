package com.omnicare.platform.conversation.infrastructure.persistence;

import com.omnicare.platform.conversation.domain.Message;
import com.omnicare.platform.conversation.domain.MessageRepository;
import com.omnicare.platform.shared.domain.ConversationId;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The JPA side of {@link MessageRepository}.
 *
 * <p>Every method runs in a transaction, and not for atomicity — several are a
 * single statement. {@code TenantAwareTransactionManager} sets
 * {@code app.tenant_id} in {@code doBegin}, so no transaction means no setting,
 * and row level security then matches nothing. Spring Data annotates the CRUD
 * methods it inherits but not derived query methods, which would leave exactly
 * the finders carrying a {@code WHERE} clause running unscoped — silently
 * returning empty results rather than failing.
 */
@Repository
@Transactional(readOnly = true)
class JpaMessageRepository implements MessageRepository {

    private final MessageJpaRepository jpa;

    JpaMessageRepository(MessageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
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
