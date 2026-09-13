package com.omnicare.platform.knowledge.domain;

import static com.omnicare.platform.knowledge.domain.DocumentStatus.FAILED;
import static com.omnicare.platform.knowledge.domain.DocumentStatus.INDEXED;
import static com.omnicare.platform.knowledge.domain.DocumentStatus.INDEXING;
import static com.omnicare.platform.knowledge.domain.DocumentStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnicare.platform.shared.domain.IllegalDocumentStateException;
import com.omnicare.platform.shared.domain.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DocumentTest {

    private static final Instant T0 = Instant.parse("2026-01-15T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-15T09:05:00Z");
    private static final Instant T2 = Instant.parse("2026-01-15T09:10:00Z");

    private static final TenantId TENANT = TenantId.generate();

    private static Document uploaded() {
        return Document.upload(TENANT, "handbook.pdf", "application/pdf", 2048L, "/data/handbook.pdf", T0);
    }

    private static Document indexing() {
        Document d = uploaded();
        d.startIndexing(T1);
        return d;
    }

    /** Burns through every retry so the document is parked in {@code FAILED}. */
    private static Document exhausted() {
        Document d = uploaded();
        for (int attempt = 0; attempt < Document.MAX_ATTEMPTS; attempt++) {
            d.startIndexing(T1);
            d.recordFailure("tika blew up", T2);
        }
        return d;
    }

    @Nested
    class Upload {

        @Test
        void landsAsPendingWithNoAttemptsYet() {
            Document d = Document.upload(TENANT, "handbook.pdf", "application/pdf", 2048L, "/data/handbook.pdf", T0);

            assertThat(d.id()).isNotNull();
            assertThat(d.tenantId()).isEqualTo(TENANT);
            assertThat(d.filename()).isEqualTo("handbook.pdf");
            assertThat(d.contentType()).isEqualTo("application/pdf");
            assertThat(d.sizeBytes()).isEqualTo(2048L);
            assertThat(d.storagePath()).isEqualTo("/data/handbook.pdf");
            assertThat(d.status()).isEqualTo(PENDING);
            assertThat(d.attempts()).isZero();
            assertThat(d.failureReason()).isEmpty();
            assertThat(d.createdAt()).isEqualTo(T0);
            assertThat(d.updatedAt()).isEqualTo(T0);
        }

        @Test
        void mintsADistinctIdentityEachTime() {
            assertThat(uploaded().id()).isNotEqualTo(uploaded().id());
        }

        @Test
        void rejectsNullTenant() {
            assertThatNullPointerException().isThrownBy(
                    () -> Document.upload(null, "a.pdf", "application/pdf", 1L, "/data/a.pdf", T0));
        }

        @Test
        void rejectsBlankFilename() {
            assertThatThrownBy(() -> Document.upload(TENANT, "  ", "application/pdf", 1L, "/data/a.pdf", T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsBlankContentType() {
            assertThatThrownBy(() -> Document.upload(TENANT, "a.pdf", "  ", 1L, "/data/a.pdf", T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsBlankStoragePath() {
            assertThatThrownBy(() -> Document.upload(TENANT, "a.pdf", "application/pdf", 1L, "  ", T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsAnEmptyFile() {
            assertThatThrownBy(() -> Document.upload(TENANT, "a.pdf", "application/pdf", 0L, "/data/a.pdf", T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsANegativeSize() {
            assertThatThrownBy(() -> Document.upload(TENANT, "a.pdf", "application/pdf", -1L, "/data/a.pdf", T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullTimestamp() {
            assertThatNullPointerException().isThrownBy(
                    () -> Document.upload(TENANT, "a.pdf", "application/pdf", 1L, "/data/a.pdf", null));
        }
    }

    @Nested
    class IndexingLifecycle {

        @Test
        void startIndexingMovesToIndexingAndCountsTheAttempt() {
            Document d = uploaded();

            d.startIndexing(T1);

            assertThat(d.status()).isEqualTo(INDEXING);
            assertThat(d.attempts()).isEqualTo(1);
            assertThat(d.updatedAt()).isEqualTo(T1);
        }

        @Test
        void markIndexedCompletesTheDocument() {
            Document d = indexing();

            d.markIndexed(T2);

            assertThat(d.status()).isEqualTo(INDEXED);
            assertThat(d.updatedAt()).isEqualTo(T2);
            assertThat(d.failureReason()).isEmpty();
        }

        @Test
        void markIndexedClearsAFailureReasonFromAnEarlierAttempt() {
            Document d = uploaded();
            d.startIndexing(T1);
            d.recordFailure("transient timeout", T1);
            d.startIndexing(T2);

            d.markIndexed(T2);

            assertThat(d.status()).isEqualTo(INDEXED);
            assertThat(d.failureReason()).isEmpty();
        }
    }

    @Nested
    class FailureAndRetry {

        @Test
        void anEarlyFailureReturnsTheDocumentToPendingForAnotherAttempt() {
            Document d = indexing();

            d.recordFailure("tika blew up", T2);

            assertThat(d.status()).isEqualTo(PENDING);
            assertThat(d.attempts()).isEqualTo(1);
            assertThat(d.failureReason()).contains("tika blew up");
            assertThat(d.updatedAt()).isEqualTo(T2);
        }

        @Test
        void theFinalAllowedAttemptFailingParksTheDocumentInFailed() {
            Document d = exhausted();

            assertThat(d.status()).isEqualTo(FAILED);
            assertThat(d.attempts()).isEqualTo(Document.MAX_ATTEMPTS);
            assertThat(d.failureReason()).contains("tika blew up");
        }

        @Test
        void everyAttemptBeforeTheLastLeavesTheDocumentRetryable() {
            Document d = uploaded();

            for (int attempt = 1; attempt < Document.MAX_ATTEMPTS; attempt++) {
                d.startIndexing(T1);
                d.recordFailure("nope", T2);
                assertThat(d.status()).isEqualTo(PENDING);
            }
        }

        @Test
        void allowsExactlyThreeAttempts() {
            assertThat(Document.MAX_ATTEMPTS).isEqualTo(3);
        }

        @Test
        void rejectsABlankFailureReason() {
            Document d = indexing();

            assertThatThrownBy(() -> d.recordFailure("  ", T2))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class IllegalTransitions {

        @Test
        void cannotMarkIndexedStraightFromPending() {
            assertThatThrownBy(() -> uploaded().markIndexed(T1))
                    .isInstanceOf(IllegalDocumentStateException.class);
        }

        @Test
        void cannotRecordAFailureWhilePending() {
            assertThatThrownBy(() -> uploaded().recordFailure("nope", T1))
                    .isInstanceOf(IllegalDocumentStateException.class);
        }

        @Test
        void cannotStartIndexingTwiceInARow() {
            Document d = indexing();

            assertThatThrownBy(() -> d.startIndexing(T2))
                    .isInstanceOf(IllegalDocumentStateException.class);
        }

        @Test
        void cannotReindexACompletedDocument() {
            Document d = indexing();
            d.markIndexed(T2);

            assertThatThrownBy(() -> d.startIndexing(T2))
                    .isInstanceOf(IllegalDocumentStateException.class);
        }

        @Test
        void cannotRestartAnExhaustedDocument() {
            assertThatThrownBy(() -> exhausted().startIndexing(T2))
                    .isInstanceOf(IllegalDocumentStateException.class);
        }

        @Test
        void illegalTransitionCarriesIdCurrentAndAttemptedTarget() {
            Document d = uploaded();

            assertThatThrownBy(() -> d.markIndexed(T1))
                    .isInstanceOfSatisfying(IllegalDocumentStateException.class, ex -> {
                        assertThat(ex.getDocumentId()).isEqualTo(d.id());
                        assertThat(ex.getCurrentStatus()).isEqualTo("PENDING");
                        assertThat(ex.getAttemptedStatus()).isEqualTo("INDEXED");
                    });
        }

        @Test
        void aRejectedTransitionLeavesTheEntityUntouched() {
            Document d = uploaded();

            assertThatThrownBy(() -> d.markIndexed(T1))
                    .isInstanceOf(IllegalDocumentStateException.class);

            assertThat(d.status()).isEqualTo(PENDING);
            assertThat(d.attempts()).isZero();
            assertThat(d.updatedAt()).isEqualTo(T0);
        }
    }

    @Nested
    class Identity {

        @Test
        void equalsItself() {
            Document d = uploaded();
            assertThat(d).isEqualTo(d).hasSameHashCodeAs(d);
        }

        @Test
        void twoUploadsAreNotEqual() {
            assertThat(uploaded()).isNotEqualTo(uploaded());
        }

        @Test
        void notEqualToNullOrAnotherType() {
            Document d = uploaded();
            assertThat(d).isNotEqualTo(null);
            assertThat(d.equals("document")).isFalse();
        }
    }
}
