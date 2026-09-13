package com.omnicare.platform.tenant.infrastructure.persistence;

import com.omnicare.platform.shared.domain.Email;
import com.omnicare.platform.shared.domain.UserId;
import com.omnicare.platform.tenant.domain.User;
import com.omnicare.platform.tenant.domain.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** The JPA side of {@link UserRepository}. */
@Repository
class JpaUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    JpaUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public User save(User user) {
        jpa.save(TenantMapper.toEntity(user));
        return user;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(TenantMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email.value()).map(TenantMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsByEmail(email.value());
    }
}
