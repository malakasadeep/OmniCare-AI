package com.omnicare.platform.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnicare.platform.shared.domain.IllegalPlanChangeException;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TenantTest {

    private static final Instant T0 = Instant.parse("2026-01-15T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-15T09:05:00Z");

    private static Tenant newTenant() {
        return Tenant.register("Acme Ltd", Plan.FREE, T0);
    }

    @Nested
    class Register {

        @Test
        void startsOnTheGivenPlanWithMatchingTimestamps() {
            Tenant t = Tenant.register("Acme Ltd", Plan.FREE, T0);

            assertThat(t.id()).isNotNull();
            assertThat(t.name()).isEqualTo("Acme Ltd");
            assertThat(t.plan()).isEqualTo(Plan.FREE);
            assertThat(t.createdAt()).isEqualTo(T0);
            assertThat(t.updatedAt()).isEqualTo(T0);
        }

        @Test
        void mintsADistinctIdentityEachTime() {
            assertThat(newTenant().id()).isNotEqualTo(newTenant().id());
        }

        @Test
        void stripsSurroundingWhitespaceFromTheName() {
            assertThat(Tenant.register("  Acme Ltd  ", Plan.FREE, T0).name()).isEqualTo("Acme Ltd");
        }

        @Test
        void rejectsNullName() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Tenant.register(null, Plan.FREE, T0));
        }

        @Test
        void rejectsBlankName() {
            assertThatThrownBy(() -> Tenant.register("   ", Plan.FREE, T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullPlan() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Tenant.register("Acme Ltd", null, T0));
        }

        @Test
        void rejectsNullTimestamp() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Tenant.register("Acme Ltd", Plan.FREE, null));
        }
    }

    @Nested
    class ChangePlan {

        @Test
        void movesToTheNewPlanAndStampsTheChange() {
            Tenant t = newTenant();

            t.changePlan(Plan.PRO, T1);

            assertThat(t.plan()).isEqualTo(Plan.PRO);
            assertThat(t.createdAt()).isEqualTo(T0);
            assertThat(t.updatedAt()).isEqualTo(T1);
        }

        @Test
        void rejectsAChangeToThePlanAlreadyHeld() {
            Tenant t = newTenant();

            assertThatThrownBy(() -> t.changePlan(Plan.FREE, T1))
                    .isInstanceOfSatisfying(IllegalPlanChangeException.class, ex -> {
                        assertThat(ex.getTenantId()).isEqualTo(t.id());
                        assertThat(ex.getPlan()).isEqualTo("FREE");
                    });
        }

        @Test
        void aRejectedChangeLeavesTheEntityUntouched() {
            Tenant t = newTenant();

            assertThatThrownBy(() -> t.changePlan(Plan.FREE, T1))
                    .isInstanceOf(IllegalPlanChangeException.class);

            assertThat(t.plan()).isEqualTo(Plan.FREE);
            assertThat(t.updatedAt()).isEqualTo(T0);
        }

        @Test
        void rejectsNullPlan() {
            Tenant t = newTenant();
            assertThatNullPointerException().isThrownBy(() -> t.changePlan(null, T1));
        }

        @Test
        void rejectsNullTimestamp() {
            Tenant t = newTenant();
            assertThatNullPointerException().isThrownBy(() -> t.changePlan(Plan.PRO, null));
        }
    }

    @Nested
    class Identity {

        @Test
        void equalsItself() {
            Tenant t = newTenant();
            assertThat(t).isEqualTo(t).hasSameHashCodeAs(t);
        }

        @Test
        void twoRegisteredTenantsAreNotEqual() {
            assertThat(newTenant()).isNotEqualTo(newTenant());
        }

        @Test
        void notEqualToNullOrAnotherType() {
            Tenant t = newTenant();
            assertThat(t).isNotEqualTo(null);
            assertThat(t.equals("tenant")).isFalse();
        }
    }
}
