package com.omnicare.platform.conversation.domain;

import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.TenantId;
import java.util.List;
import java.util.Optional;

/** Persistence contract for {@link Conversation}. */
public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Optional<Conversation> findById(ConversationId id);

    long countForTenant(TenantId tenantId);

    /**
     * One page of a tenant's conversations, newest first.
     *
     * <p>Takes plain {@code page} and {@code size} rather than a Spring Data
     * {@code Pageable}: the domain declares what it needs, and letting a
     * persistence library's type into this signature would make every caller
     * depend on it too.
     */
    List<Conversation> findRecentForTenant(TenantId tenantId, int page, int size);
}
