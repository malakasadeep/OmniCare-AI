package com.omnicare.platform.tenant.domain;

import com.omnicare.platform.shared.domain.Email;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import java.util.Optional;

/** Persistence contract for {@link User}. See {@link TenantRepository}. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    /**
     * Email is unique per tenant rather than globally, so a lookup by address
     * alone would be ambiguous.
     */
    Optional<User> findByTenantAndEmail(TenantId tenantId, Email email);
}
