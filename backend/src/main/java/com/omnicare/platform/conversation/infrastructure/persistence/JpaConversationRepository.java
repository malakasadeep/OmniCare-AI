package com.omnicare.platform.conversation.infrastructure.persistence;

import com.omnicare.platform.conversation.domain.Conversation;
import com.omnicare.platform.conversation.domain.ConversationRepository;
import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.TenantId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The JPA side of {@link ConversationRepository}.
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
class JpaConversationRepository implements ConversationRepository {

    private final ConversationJpaRepository jpa;

    JpaConversationRepository(ConversationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
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

    @Override
    public List<Conversation> findRecentForTenant(TenantId tenantId, int page, int size) {
        return jpa.findByTenantIdOrderByCreatedAtDescIdDesc(
                        tenantId.value(), PageRequest.of(page, size)).stream()
                .map(ConversationMapper::toDomain)
                .toList();
    }
}
