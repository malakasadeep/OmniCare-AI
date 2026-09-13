package com.omnicare.platform.conversation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.omnicare.platform.TestcontainersConfiguration;
import com.omnicare.platform.conversation.domain.Conversation;
import com.omnicare.platform.conversation.domain.ConversationRepository;
import com.omnicare.platform.conversation.domain.ConversationStatus;
import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.Email;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import com.omnicare.platform.shared.domain.VisitorId;
import com.omnicare.platform.tenant.domain.Plan;
import com.omnicare.platform.tenant.domain.Tenant;
import com.omnicare.platform.tenant.domain.TenantRepository;
import com.omnicare.platform.tenant.domain.User;
import com.omnicare.platform.tenant.domain.UserRepository;
import com.omnicare.platform.tenant.domain.UserRole;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Proves the domain survives a round trip through Postgres: what comes back out
 * of the repository is equivalent to what went in, not merely a row with the
 * same primary key.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConversationRepositoryIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-01-15T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-15T09:05:00Z");
    private static final Instant T2 = Instant.parse("2026-01-15T09:10:00Z");

    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserRepository users;

    private TenantId tenantId;

    @BeforeEach
    void createOwningTenant() {
        tenantId = tenants.save(Tenant.register("Acme Ltd", Plan.FREE, T0)).id();
    }

    @Test
    void savingAndReloadingReturnsAnEquivalentConversation() {
        Conversation saved = conversations.save(
                Conversation.start(tenantId, VisitorId.generate(), T0));

        Conversation reloaded = conversations.findById(saved.id()).orElseThrow();

        assertThat(reloaded.id()).isEqualTo(saved.id());
        assertThat(reloaded.tenantId()).isEqualTo(saved.tenantId());
        assertThat(reloaded.visitorId()).isEqualTo(saved.visitorId());
        assertThat(reloaded.status()).isEqualTo(ConversationStatus.BOT_ACTIVE);
        assertThat(reloaded.createdAt()).isEqualTo(T0);
        assertThat(reloaded.updatedAt()).isEqualTo(T0);
        assertThat(reloaded.assignedOperator()).isEmpty();
        assertThat(reloaded.escalationReason()).isEmpty();
    }

    @Test
    void aReloadedConversationStillEnforcesItsStateMachine() {
        Conversation saved = conversations.save(
                Conversation.start(tenantId, VisitorId.generate(), T0));

        Conversation reloaded = conversations.findById(saved.id()).orElseThrow();
        reloaded.escalateToHuman("wants a person", T1);

        assertThat(reloaded.status()).isEqualTo(ConversationStatus.AWAITING_HUMAN);
    }

    @Test
    void escalationReasonAndOperatorSurviveTheRoundTrip() {
        UserId operator = users.save(User.register(
                tenantId, new Email("agent@acme.test"), "$2a$10$hash", UserRole.AGENT, T0)).id();

        Conversation conversation = Conversation.start(tenantId, VisitorId.generate(), T0);
        conversation.escalateToHuman("refund request", T1);
        conversation.assignOperator(operator, T2);
        conversations.save(conversation);

        Conversation reloaded = conversations.findById(conversation.id()).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(ConversationStatus.HUMAN_ACTIVE);
        assertThat(reloaded.escalationReason()).contains("refund request");
        assertThat(reloaded.assignedOperator()).contains(operator);
        assertThat(reloaded.updatedAt()).isEqualTo(T2);
    }

    @Test
    void savingAnAlreadyPersistedConversationUpdatesItRatherThanDuplicating() {
        Conversation conversation = conversations.save(
                Conversation.start(tenantId, VisitorId.generate(), T0));

        conversation.resolve(T1);
        conversations.save(conversation);

        Conversation reloaded = conversations.findById(conversation.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ConversationStatus.RESOLVED);
        assertThat(conversations.countForTenant(tenantId)).isEqualTo(1);
    }

    @Test
    void findByIdIsEmptyForAnUnknownConversation() {
        Optional<Conversation> found = conversations.findById(ConversationId.generate());

        assertThat(found).isEmpty();
    }
}
