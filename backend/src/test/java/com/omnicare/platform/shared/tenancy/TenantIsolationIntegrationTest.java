package com.omnicare.platform.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.omnicare.platform.TestcontainersConfiguration;
import com.omnicare.platform.conversation.domain.Conversation;
import com.omnicare.platform.conversation.domain.ConversationRepository;
import com.omnicare.platform.conversation.domain.Message;
import com.omnicare.platform.conversation.domain.MessageRepository;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.VisitorId;
import com.omnicare.platform.shared.security.AuthenticatedUser;
import com.omnicare.platform.shared.security.JwtService;
import com.omnicare.platform.tenant.domain.Plan;
import com.omnicare.platform.tenant.domain.Tenant;
import com.omnicare.platform.tenant.domain.TenantRepository;
import com.omnicare.platform.tenant.domain.User;
import com.omnicare.platform.tenant.domain.UserRepository;
import com.omnicare.platform.tenant.domain.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Day 5's "done when", in two halves.
 *
 * <p>The first is the stated acceptance test: a request authenticated as tenant
 * A gets a 404 for tenant B's conversation. The second is the stronger claim
 * underneath it — that forgetting {@code WHERE tenant_id} leaks nothing — which
 * is checked with deliberately unfiltered SQL, because an application-level test
 * can only ever prove the application remembered.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "omnicare.jwt.secret=" + TenantIsolationIntegrationTest.TEST_SECRET,
        "omnicare.jwt.access-token-ttl=15m",
        "omnicare.jwt.refresh-token-ttl=30d"
})
@AutoConfigureMockMvc
class TenantIsolationIntegrationTest {

    static final String TEST_SECRET = "isolation-test-signing-secret-at-least-32-bytes";

    private static final Instant T0 = Instant.parse("2026-01-15T09:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserRepository users;

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DataSource dataSource;

    private TenantId tenantA;
    private TenantId tenantB;
    private String tokenForA;
    private Conversation conversationOfA;
    private Conversation conversationOfB;

    /** Runs a unit of work with the tenant context set, as a real request would. */
    private static <T> T as(TenantId tenant, Supplier<T> work) {
        TenantContext.set(tenant);
        try {
            return work.get();
        } finally {
            TenantContext.clear();
        }
    }

    private String accessTokenFor(TenantId tenant) {
        User user = as(tenant, () -> users.save(User.register(
                tenant,
                new com.omnicare.platform.shared.domain.Email(
                        "op-" + UUID.randomUUID() + "@example.test"),
                "$2a$10$hash",
                UserRole.OWNER,
                T0)));
        return jwtService.issueAccessToken(
                new AuthenticatedUser(user.id(), tenant, user.role().name()));
    }

    @BeforeEach
    void seedTwoTenants() {
        tenantA = tenants.save(Tenant.register("Tenant A", Plan.FREE, T0)).id();
        tenantB = tenants.save(Tenant.register("Tenant B", Plan.FREE, T0)).id();

        conversationOfA = as(tenantA, () ->
                conversations.save(Conversation.start(tenantA, VisitorId.generate(), T0)));
        conversationOfB = as(tenantB, () ->
                conversations.save(Conversation.start(tenantB, VisitorId.generate(), T0)));

        as(tenantB, () -> messages.save(
                Message.fromUser(tenantB, conversationOfB.id(), "tenant B's secret", T0)));

        tokenForA = accessTokenFor(tenantA);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Nested
    class OverHttp {

        @Test
        void tenantAGetsItsOwnConversation() throws Exception {
            mvc.perform(get("/api/conversations/" + conversationOfA.id().value())
                            .header("Authorization", "Bearer " + tokenForA))
                    .andExpect(status().isOk());
        }

        @Test
        void tenantAGets404ForTenantBsConversation() throws Exception {
            mvc.perform(get("/api/conversations/" + conversationOfB.id().value())
                            .header("Authorization", "Bearer " + tokenForA))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anUnknownIdIsIndistinguishableFromAnotherTenantsId() throws Exception {
            String other = mvc.perform(get("/api/conversations/" + conversationOfB.id().value())
                            .header("Authorization", "Bearer " + tokenForA))
                    .andReturn().getResponse().getContentAsString();
            String unknown = mvc.perform(get("/api/conversations/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + tokenForA))
                    .andReturn().getResponse().getContentAsString();

            assertThat(other).isEqualTo(unknown);
        }
    }

    @Nested
    class ThroughTheRepository {

        @Test
        void findByIdCannotReachAnotherTenantsConversation() {
            assertThat(as(tenantA, () -> conversations.findById(conversationOfB.id()))).isEmpty();
        }

        @Test
        void eachTenantSeesOnlyItsOwnConversationInACount() {
            assertThat(as(tenantA, () -> conversations.countForTenant(tenantA))).isEqualTo(1);
            assertThat(as(tenantA, () -> conversations.countForTenant(tenantB))).isZero();
        }

        @Test
        void messagesOfAnotherTenantAreInvisible() {
            assertThat(as(tenantA, () -> messages.findByConversation(conversationOfB.id())))
                    .isEmpty();
            assertThat(as(tenantB, () -> messages.findByConversation(conversationOfB.id())))
                    .hasSize(1);
        }

        @Test
        void withNoTenantInContextNothingIsVisibleAtAll() {
            TenantContext.clear();

            assertThat(conversations.findById(conversationOfA.id())).isEmpty();
            assertThat(conversations.findById(conversationOfB.id())).isEmpty();
        }
    }

    /**
     * The part that matters. These queries carry no tenant predicate at all —
     * they are what a careless {@code findAll()} or a hand-written report query
     * compiles down to. Postgres adds the filter itself.
     */
    @Nested
    class WithoutAnyTenantPredicate {

        private List<UUID> conversationIdsVisibleTo(TenantId tenant) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<List<UUID>>) connection -> {
                connection.setAutoCommit(false);
                try (var scope = connection.prepareStatement(
                        "select set_config('app.tenant_id', ?, true)")) {
                    scope.setString(1, tenant == null ? "" : tenant.value().toString());
                    scope.execute();
                }
                List<UUID> ids = new java.util.ArrayList<>();
                try (var statement = connection.prepareStatement("select id from conversations");
                     var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        ids.add(rows.getObject("id", UUID.class));
                    }
                }
                connection.rollback();
                connection.setAutoCommit(true);
                return ids;
            });
        }

        @Test
        void anUnfilteredSelectReturnsOnlyTheCurrentTenantsRows() {
            assertThat(conversationIdsVisibleTo(tenantA))
                    .contains(conversationOfA.id().value())
                    .doesNotContain(conversationOfB.id().value());

            assertThat(conversationIdsVisibleTo(tenantB))
                    .contains(conversationOfB.id().value())
                    .doesNotContain(conversationOfA.id().value());
        }

        @Test
        void anUnfilteredSelectWithNoTenantSetReturnsNothing() {
            assertThat(conversationIdsVisibleTo(null)).isEmpty();
        }
    }
}
