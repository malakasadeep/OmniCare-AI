package com.omnicare.platform.shared.tenancy;

import jakarta.persistence.EntityManager;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Stamps every transaction with the current tenant, so the RLS policies added in
 * {@code V3__rls.sql} have something to compare against.
 *
 * <p>Why the transaction manager and not a filter: the setting has to land on
 * the same connection the transaction will use, after that connection has been
 * acquired. {@code doBegin} is the first moment both are true.
 *
 * <p>Why {@code SET LOCAL} semantics (the {@code true} argument to
 * {@code set_config}) and not {@code SET}: connections are pooled. A plain
 * {@code SET} lives as long as the connection, so the tenant would outlive the
 * request and be inherited by whichever tenant borrowed that connection next —
 * a cross-tenant leak that would only show up under concurrency. {@code SET
 * LOCAL} is reverted by Postgres at commit or rollback, so a connection always
 * returns to the pool unscoped.
 *
 * <p>A transaction with no tenant in context is left unscoped, which makes the
 * policies match nothing. That is the correct failure mode: registration and
 * login legitimately run before any tenant is known, and everything else should
 * see no rows rather than the wrong ones.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    private static final String SET_TENANT = "select set_config('app.tenant_id', :tenantId, true)";

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        TenantContext.currentTenant().ifPresent(tenantId ->
                entityManagerFor(transaction)
                        .createNativeQuery(SET_TENANT)
                        .setParameter("tenantId", tenantId.value().toString())
                        .getSingleResult());
    }

    /**
     * The EntityManager Spring has just bound for this transaction. Going
     * through the bound resource rather than creating one guarantees the setting
     * lands on the transaction's own connection.
     */
    private EntityManager entityManagerFor(Object transaction) {
        EntityManagerHolder holder = (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder == null) {
            throw new IllegalStateException(
                    "No EntityManager bound after doBegin; cannot scope transaction to a tenant");
        }
        return holder.getEntityManager();
    }
}
