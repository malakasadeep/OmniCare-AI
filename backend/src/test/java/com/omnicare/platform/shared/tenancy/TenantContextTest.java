package com.omnicare.platform.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.omnicare.platform.shared.domain.TenantId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void isEmptyBeforeAnythingSetsIt() {
        assertThat(TenantContext.currentTenant()).isEmpty();
    }

    @Test
    void returnsWhateverWasSet() {
        TenantId tenant = TenantId.generate();

        TenantContext.set(tenant);

        assertThat(TenantContext.currentTenant()).contains(tenant);
    }

    @Test
    void clearRemovesIt() {
        TenantContext.set(TenantId.generate());

        TenantContext.clear();

        assertThat(TenantContext.currentTenant()).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> TenantContext.set(null));
    }

    /**
     * The whole reason this is a ThreadLocal. Tomcat hands each request its own
     * thread, so one request's tenant must be invisible to another's — a leak
     * here would be a cross-tenant data leak.
     */
    @Test
    void oneThreadCannotSeeAnotherThreadsTenant() throws Exception {
        TenantId mine = TenantId.generate();
        TenantContext.set(mine);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var seenByOtherThread = CompletableFuture.supplyAsync(
                    TenantContext::currentTenant, executor).get(5, TimeUnit.SECONDS);

            assertThat(seenByOtherThread).isEmpty();
            assertThat(TenantContext.currentTenant()).contains(mine);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aThreadThatSetsItsOwnTenantDoesNotDisturbThisOne() throws Exception {
        TenantId mine = TenantId.generate();
        TenantContext.set(mine);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture.runAsync(() -> TenantContext.set(TenantId.generate()), executor)
                    .get(5, TimeUnit.SECONDS);

            assertThat(TenantContext.currentTenant()).contains(mine);
        } finally {
            executor.shutdownNow();
        }
    }
}
