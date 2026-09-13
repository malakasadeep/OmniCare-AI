package com.omnicare.platform.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MessageTest {

    private static final Instant T0 = Instant.parse("2026-01-15T09:00:00Z");

    private static final TenantId TENANT = TenantId.generate();
    private static final ConversationId CONVERSATION = ConversationId.generate();

    @Nested
    class Factories {

        @Test
        void fromUserCarriesTheUserRole() {
            Message m = Message.fromUser(TENANT, CONVERSATION, "where is my order?", T0);

            assertThat(m.id()).isNotNull();
            assertThat(m.tenantId()).isEqualTo(TENANT);
            assertThat(m.conversationId()).isEqualTo(CONVERSATION);
            assertThat(m.role()).isEqualTo(MessageRole.USER);
            assertThat(m.content()).isEqualTo("where is my order?");
            assertThat(m.createdAt()).isEqualTo(T0);
        }

        @Test
        void fromAssistantCarriesTheAssistantRole() {
            Message m = Message.fromAssistant(TENANT, CONVERSATION, "it ships tomorrow", T0);

            assertThat(m.role()).isEqualTo(MessageRole.ASSISTANT);
            assertThat(m.content()).isEqualTo("it ships tomorrow");
        }

        @Test
        void systemCarriesTheSystemRole() {
            Message m = Message.system(TENANT, CONVERSATION, "you are a support agent", T0);

            assertThat(m.role()).isEqualTo(MessageRole.SYSTEM);
        }

        @Test
        void mintsADistinctIdentityEachTime() {
            Message a = Message.fromUser(TENANT, CONVERSATION, "hello", T0);
            Message b = Message.fromUser(TENANT, CONVERSATION, "hello", T0);

            assertThat(a.id()).isNotEqualTo(b.id());
        }
    }

    @Nested
    class ContentRules {

        @Test
        void stripsSurroundingWhitespace() {
            Message m = Message.fromUser(TENANT, CONVERSATION, "  hello  ", T0);

            assertThat(m.content()).isEqualTo("hello");
        }

        @Test
        void preservesInternalNewlines() {
            Message m = Message.fromAssistant(TENANT, CONVERSATION, "line one\nline two", T0);

            assertThat(m.content()).isEqualTo("line one\nline two");
        }

        @Test
        void rejectsNullContent() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Message.fromUser(TENANT, CONVERSATION, null, T0));
        }

        @Test
        void rejectsBlankContent() {
            assertThatThrownBy(() -> Message.fromUser(TENANT, CONVERSATION, "   ", T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ArgumentValidation {

        @Test
        void rejectsNullTenant() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Message.fromUser(null, CONVERSATION, "hi", T0));
        }

        @Test
        void rejectsNullConversation() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Message.fromUser(TENANT, null, "hi", T0));
        }

        @Test
        void rejectsNullTimestamp() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Message.fromUser(TENANT, CONVERSATION, "hi", null));
        }
    }

    @Nested
    class Identity {

        @Test
        void equalsItself() {
            Message m = Message.fromUser(TENANT, CONVERSATION, "hi", T0);
            assertThat(m).isEqualTo(m).hasSameHashCodeAs(m);
        }

        @Test
        void twoMessagesWithTheSameContentAreNotEqual() {
            assertThat(Message.fromUser(TENANT, CONVERSATION, "hi", T0))
                    .isNotEqualTo(Message.fromUser(TENANT, CONVERSATION, "hi", T0));
        }

        @Test
        void notEqualToNullOrAnotherType() {
            Message m = Message.fromUser(TENANT, CONVERSATION, "hi", T0);
            assertThat(m).isNotEqualTo(null);
            assertThat(m.equals("message")).isFalse();
        }
    }
}
