package com.omnicare.platform.conversation.domain;

import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.TenantId;
import java.util.Optional;

/** Persistence contract for {@link Conversation}. */
public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Optional<Conversation> findById(ConversationId id);

    long countForTenant(TenantId tenantId);
}
