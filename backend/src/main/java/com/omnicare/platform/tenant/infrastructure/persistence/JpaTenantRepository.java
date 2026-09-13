package com.omnicare.platform.tenant.infrastructure.persistence;

import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.tenant.domain.Tenant;
import com.omnicare.platform.tenant.domain.TenantRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * The JPA side of {@link TenantRepository}: takes domain objects in, hands
 * domain objects back, and keeps {@link TenantEntity} entirely inside this
 * package.
 */
@Repository
class JpaTenantRepository implements TenantRepository {

    private final TenantJpaRepository jpa;

    JpaTenantRepository(TenantJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Tenant save(Tenant tenant) {
        jpa.save(TenantMapper.toEntity(tenant));
        return tenant;
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return jpa.findById(id.value()).map(TenantMapper::toDomain);
    }
}
