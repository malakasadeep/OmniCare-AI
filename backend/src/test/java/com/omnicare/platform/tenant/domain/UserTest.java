package com.omnicare.platform.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnicare.platform.shared.domain.Email;
import com.omnicare.platform.shared.domain.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant T0 = Instant.parse("2026-01-15T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-15T09:05:00Z");

    private static final TenantId TENANT = TenantId.generate();
    private static final Email EMAIL = new Email("alice@example.com");
    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuv";

    private static User newOwner() {
        return User.register(TENANT, EMAIL, HASH, UserRole.OWNER, T0);
    }

    @Nested
    class Register {

        @Test
        void carriesTenantEmailRoleAndTimestamps() {
            User u = User.register(TENANT, EMAIL, HASH, UserRole.OWNER, T0);

            assertThat(u.id()).isNotNull();
            assertThat(u.tenantId()).isEqualTo(TENANT);
            assertThat(u.email()).isEqualTo(EMAIL);
            assertThat(u.role()).isEqualTo(UserRole.OWNER);
            assertThat(u.passwordHash()).isEqualTo(HASH);
            assertThat(u.createdAt()).isEqualTo(T0);
            assertThat(u.updatedAt()).isEqualTo(T0);
        }

        @Test
        void mintsADistinctIdentityEachTime() {
            assertThat(newOwner().id()).isNotEqualTo(newOwner().id());
        }

        @Test
        void rejectsNullTenant() {
            assertThatNullPointerException()
                    .isThrownBy(() -> User.register(null, EMAIL, HASH, UserRole.OWNER, T0));
        }

        @Test
        void rejectsNullEmail() {
            assertThatNullPointerException()
                    .isThrownBy(() -> User.register(TENANT, null, HASH, UserRole.OWNER, T0));
        }

        @Test
        void rejectsNullPasswordHash() {
            assertThatNullPointerException()
                    .isThrownBy(() -> User.register(TENANT, EMAIL, null, UserRole.OWNER, T0));
        }

        @Test
        void rejectsBlankPasswordHash() {
            assertThatThrownBy(() -> User.register(TENANT, EMAIL, "  ", UserRole.OWNER, T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullRole() {
            assertThatNullPointerException()
                    .isThrownBy(() -> User.register(TENANT, EMAIL, HASH, null, T0));
        }

        @Test
        void rejectsNullTimestamp() {
            assertThatNullPointerException()
                    .isThrownBy(() -> User.register(TENANT, EMAIL, HASH, UserRole.OWNER, null));
        }
    }

    @Nested
    class ChangePassword {

        @Test
        void replacesTheStoredHashAndStampsTheChange() {
            User u = newOwner();

            u.changePassword("$2a$10$zyxwvutsrqponmlkjihgfe", T1);

            assertThat(u.passwordHash()).isEqualTo("$2a$10$zyxwvutsrqponmlkjihgfe");
            assertThat(u.updatedAt()).isEqualTo(T1);
            assertThat(u.createdAt()).isEqualTo(T0);
        }

        @Test
        void rejectsNullHash() {
            User u = newOwner();
            assertThatNullPointerException().isThrownBy(() -> u.changePassword(null, T1));
        }

        @Test
        void rejectsBlankHash() {
            User u = newOwner();
            assertThatThrownBy(() -> u.changePassword("   ", T1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullTimestamp() {
            User u = newOwner();
            assertThatNullPointerException().isThrownBy(() -> u.changePassword(HASH, null));
        }
    }

    @Nested
    class Roles {

        @Test
        void ownerIsRecognisedAsOwner() {
            assertThat(newOwner().isOwner()).isTrue();
        }

        @Test
        void agentIsNotOwner() {
            User agent = User.register(TENANT, EMAIL, HASH, UserRole.AGENT, T0);
            assertThat(agent.isOwner()).isFalse();
        }
    }

    @Nested
    class Identity {

        @Test
        void equalsItself() {
            User u = newOwner();
            assertThat(u).isEqualTo(u).hasSameHashCodeAs(u);
        }

        @Test
        void twoRegisteredUsersAreNotEqual() {
            assertThat(newOwner()).isNotEqualTo(newOwner());
        }

        @Test
        void notEqualToNullOrAnotherType() {
            User u = newOwner();
            assertThat(u).isNotEqualTo(null);
            assertThat(u.equals("user")).isFalse();
        }

        @Test
        void toStringDoesNotLeakThePasswordHash() {
            assertThat(newOwner().toString()).doesNotContain(HASH);
        }
    }
}
