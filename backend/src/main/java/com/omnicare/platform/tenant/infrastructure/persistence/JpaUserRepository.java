package com.omnicare.platform.tenant.infrastructure.persistence;

import com.omnicare.platform.shared.domain.Email;
import com.omnicare.platform.shared.domain.UserId;
import com.omnicare.platform.tenant.domain.User;
import com.omnicare.platform.tenant.domain.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The JPA side of {@link UserRepository}.
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
class JpaUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    JpaUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
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
