package com.omnicare.platform.conversation.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code conversations}. Not exposed beyond this package. */
interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {

    long countByTenantId(UUID tenantId);
}
