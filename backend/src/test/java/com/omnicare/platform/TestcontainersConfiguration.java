package com.omnicare.platform;

import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres for the integration tests — the same {@code pgvector/pgvector:pg16}
 * image production runs, rather than H2. H2 would not have pgvector, would not
 * have row level security, and would happily accept SQL Postgres rejects.
 *
 * <p>Note what is <em>not</em> here: {@code @ServiceConnection}. That would hand
 * Spring the container's own credentials, and a Testcontainers Postgres runs its
 * configured user as a superuser — who bypasses row level security entirely,
 * quietly turning every isolation test green for the wrong reason. Instead the
 * init script creates the same non-superuser role dev and production use, and
 * the connection details below point at that role.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final String APP_ROLE = "omnicare_app";

    @Bean
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                .withInitScript("db/testcontainers-init.sql");
    }

    @Bean
    public JdbcConnectionDetails jdbcConnectionDetails(PostgreSQLContainer<?> container) {
        return new JdbcConnectionDetails() {

            @Override
            public String getJdbcUrl() {
                return container.getJdbcUrl();
            }

            @Override
            public String getUsername() {
                return APP_ROLE;
            }

            @Override
            public String getPassword() {
                return APP_ROLE;
            }
        };
    }
}
