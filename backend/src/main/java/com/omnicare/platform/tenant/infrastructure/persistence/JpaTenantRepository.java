package com.omnicare.platform.tenant.infrastructure.persistence;

import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.tenant.domain.Tenant;
import com.omnicare.platform.tenant.domain.TenantRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The JPA side of {@link TenantRepository}.
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
class JpaTenantRepository implements TenantRepository {

    private final TenantJpaRepository jpa;

    JpaTenantRepository(TenantJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Tenant save(Tenant tenant) {
        jpa.save(TenantMapper.toEntity(tenant));
        return tenant;
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return jpa.findById(id.value()).map(TenantMapper::toDomain);
    }
}
