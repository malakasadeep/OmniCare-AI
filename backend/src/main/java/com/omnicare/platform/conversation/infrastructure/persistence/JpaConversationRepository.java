package com.omnicare.platform.conversation.infrastructure.persistence;

import com.omnicare.platform.conversation.domain.Conversation;
import com.omnicare.platform.conversation.domain.ConversationRepository;
import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.TenantId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link ConversationRepository}. */
@Repository
class JpaConversationRepository implements ConversationRepository {

    private final ConversationJpaRepository jpa;

    JpaConversationRepository(ConversationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Conversation save(Conversation conversation) {
        jpa.save(ConversationMapper.toEntity(conversation));
        return conversation;
    }

    @Override
    public Optional<Conversation> findById(ConversationId id) {
        return jpa.findById(id.value()).map(ConversationMapper::toDomain);
    }

    @Override
    public long countForTenant(TenantId tenantId) {
        return jpa.countByTenantId(tenantId.value());
    }
}
