package com.omnicare.platform.tenant.domain;

import com.omnicare.platform.shared.domain.TenantId;
import java.util.Optional;

/**
 * Persistence contract for {@link Tenant}, declared in the domain and
 * implemented in {@code infrastructure.persistence}.
 *
 * <p>The direction matters: the domain names what it needs and the JPA code
 * depends on this interface, never the other way round. Nothing in this package
 * knows that Postgres or Hibernate exist.
 */
public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(TenantId id);
}
