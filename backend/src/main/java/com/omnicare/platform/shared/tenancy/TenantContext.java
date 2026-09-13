package com.omnicare.platform.shared.tenancy;

import com.omnicare.platform.shared.domain.TenantId;
import java.util.Objects;
import java.util.Optional;

/**
 * The tenant the current request is acting for.
 *
 * <p>Request-scoped state held in a {@link ThreadLocal}: the servlet container
 * gives each request its own thread, so one request's tenant is invisible to
 * every other. That isolation is the entire point — a value leaking between
 * threads here is a cross-tenant data leak.
 *
 * <p>A {@code ThreadLocal} that is set and never cleared outlives the request,
 * because pooled threads are reused. The next request to land on that thread
 * would inherit the previous tenant, so {@link TenantFilter} clears it in a
 * {@code finally} block without exception.
 *
 * <p>Deliberately absent: any way to ask "which tenant, or else the default".
 * There is no default. Code that needs a tenant and has none must fail rather
 * than guess.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantId tenantId) {
        CURRENT.set(Objects.requireNonNull(tenantId, "tenantId must not be null"));
    }

    public static Optional<TenantId> currentTenant() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
