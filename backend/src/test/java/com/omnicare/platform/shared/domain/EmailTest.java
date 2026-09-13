package com.omnicare.platform.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void rejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Email(null));
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new Email("   "))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void rejectsValueWithoutAtSign() {
        assertThatThrownBy(() -> new Email("alice.example.com"))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void normalisesToLowercase() {
        assertThat(new Email("Alice@Example.COM").value())
                .isEqualTo("alice@example.com");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(new Email("  alice@example.com  ").value())
                .isEqualTo("alice@example.com");
    }

    @Test
    void isEqualByValueAfterNormalisation() {
        assertThat(new Email("Alice@Example.com"))
                .isEqualTo(new Email("alice@example.com"));
    }

    @Test
    void invalidEmailExceptionKeepsTheOffendingValue() {
        assertThatThrownBy(() -> new Email("nope"))
                .isInstanceOf(InvalidEmailException.class)
                .extracting(ex -> ((InvalidEmailException) ex).getProvidedValue())
                .isEqualTo("nope");
    }
}
