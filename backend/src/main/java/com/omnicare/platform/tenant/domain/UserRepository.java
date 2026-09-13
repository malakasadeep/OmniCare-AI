package com.omnicare.platform.tenant.domain;

import com.omnicare.platform.shared.domain.Email;
import com.omnicare.platform.shared.domain.UserId;
import java.util.Optional;

/** Persistence contract for {@link User}. See {@link TenantRepository}. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    /** Email is globally unique, which is what makes login by address alone work. */
    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);
}
