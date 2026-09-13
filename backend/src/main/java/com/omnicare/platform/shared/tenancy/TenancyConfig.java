package com.omnicare.platform.shared.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wires the tenancy pieces together.
 *
 * <p>The transaction manager bean replaces Boot's default {@code JpaTransactionManager}:
 * Boot backs off when one is already defined, so declaring it here puts every
 * transaction in the application through {@link TenantAwareTransactionManager}
 * without any call site opting in. Tenant scoping that has to be remembered is
 * tenant scoping that will be forgotten.
 */
@Configuration
class TenancyConfig {

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        TenantAwareTransactionManager manager = new TenantAwareTransactionManager();
        manager.setEntityManagerFactory(entityManagerFactory);
        return manager;
    }
}
