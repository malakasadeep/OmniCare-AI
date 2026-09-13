package com.omnicare.platform.tenant.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code tenants}. Not exposed beyond this package. */
interface TenantJpaRepository extends JpaRepository<TenantEntity, UUID> {
}
