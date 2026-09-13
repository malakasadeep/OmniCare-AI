package com.omnicare.platform.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionsTest {

    @Test
    void illegalConversationStateExceptionCarriesIdCurrentAndTarget() {
        ConversationId id = ConversationId.generate();

        IllegalConversationStateException ex =
                new IllegalConversationStateException(id, "BOT_ACTIVE", "HUMAN_ACTIVE");

        assertThat(ex.getConversationId()).isEqualTo(id);
        assertThat(ex.getCurrentStatus()).isEqualTo("BOT_ACTIVE");
        assertThat(ex.getAttemptedStatus()).isEqualTo("HUMAN_ACTIVE");
        assertThat(ex.getMessage())
                .contains(id.value().toString(), "BOT_ACTIVE", "HUMAN_ACTIVE");
    }

    @Test
    void illegalConversationStateExceptionIsAnUncheckedDomainException() {
        assertThat(new IllegalConversationStateException(ConversationId.generate(), "A", "B"))
                .isInstanceOf(DomainException.class)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void illegalDocumentStateExceptionCarriesIdCurrentAndTarget() {
        DocumentId id = DocumentId.generate();

        IllegalDocumentStateException ex =
                new IllegalDocumentStateException(id, "PENDING", "INDEXED");

        assertThat(ex.getDocumentId()).isEqualTo(id);
        assertThat(ex.getCurrentStatus()).isEqualTo("PENDING");
        assertThat(ex.getAttemptedStatus()).isEqualTo("INDEXED");
        assertThat(ex.getMessage())
                .contains(id.value().toString(), "PENDING", "INDEXED");
    }

    @Test
    void invalidEmailExceptionKeepsTheOffendingValueAndIsADomainException() {
        InvalidEmailException ex = new InvalidEmailException("not-an-email");

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.getProvidedValue()).isEqualTo("not-an-email");
        assertThat(ex.getMessage()).contains("not-an-email");
    }

    @Test
    void illegalPlanChangeExceptionCarriesTenantAndPlan() {
        TenantId id = TenantId.generate();

        IllegalPlanChangeException ex = new IllegalPlanChangeException(id, "PRO");

        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.getTenantId()).isEqualTo(id);
        assertThat(ex.getPlan()).isEqualTo("PRO");
        assertThat(ex.getMessage()).contains(id.value().toString(), "PRO");
    }
}
