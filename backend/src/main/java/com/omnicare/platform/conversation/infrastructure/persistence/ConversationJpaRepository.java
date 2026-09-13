package com.omnicare.platform.conversation.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code conversations}. Not exposed beyond this package. */
interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {

    long countByTenantId(UUID tenantId);

    /**
     * Ordered by id as well as timestamp so the sort is total. Two rows sharing
     * a created_at would otherwise come back in an arbitrary order, and an
     * arbitrary order across pages means rows repeat on one page and vanish
     * from another.
     */
    List<ConversationEntity> findByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);
}
