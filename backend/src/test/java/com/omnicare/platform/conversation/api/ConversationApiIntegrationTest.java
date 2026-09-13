package com.omnicare.platform.conversation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnicare.platform.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Day 6's "done when": create a conversation, post three messages, read them
 * back — all through the API, as a client would.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "omnicare.jwt.secret=" + ConversationApiIntegrationTest.TEST_SECRET,
        "omnicare.jwt.access-token-ttl=15m",
        "omnicare.jwt.refresh-token-ttl=30d"
})
@AutoConfigureMockMvc
class ConversationApiIntegrationTest {

    static final String TEST_SECRET = "conversation-api-test-secret-at-least-32-bytes";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String token;

    private String registerTenant() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Acme Ltd","email":"owner-%s@acme.test",
                                 "password":"correct horse battery staple"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String createConversation() throws Exception {
        String body = mvc.perform(authed(post("/api/conversations"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private void postMessage(String conversationId, String content) throws Exception {
        mvc.perform(authed(post("/api/conversations/" + conversationId + "/messages"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new java.util.HashMap<>() {{
                            put("content", content);
                        }})))
                .andExpect(status().isCreated());
    }

    @BeforeEach
    void authenticate() throws Exception {
        token = registerTenant();
    }

    @Nested
    class CreateConversation {

        @Test
        void returnsANewConversationInBotActive() throws Exception {
            mvc.perform(authed(post("/api/conversations"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.visitorId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("BOT_ACTIVE"));
        }

        @Test
        void acceptsACallerSuppliedVisitorIdSoOneVisitorCanBeTrackedAcrossConversations()
                throws Exception {
            String visitorId = UUID.randomUUID().toString();

            mvc.perform(authed(post("/api/conversations"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"visitorId":"%s"}
                                    """.formatted(visitorId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.visitorId").value(visitorId));
        }

        @Test
        void requiresAToken() throws Exception {
            mvc.perform(post("/api/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class PostAndReadMessages {

        @Test
        void threeMessagesComeBackInTheOrderTheyWereSent() throws Exception {
            String conversationId = createConversation();

            postMessage(conversationId, "first");
            postMessage(conversationId, "second");
            postMessage(conversationId, "third");

            String body = mvc.perform(authed(get("/api/conversations/" + conversationId + "/messages")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode messages = json.readTree(body);
            // Each user message is answered, so the transcript alternates.
            assertThat(messages).hasSize(6);
            assertThat(messages.get(0).get("role").asText()).isEqualTo("USER");
            assertThat(messages.get(0).get("content").asText()).isEqualTo("first");
            assertThat(messages.get(1).get("role").asText()).isEqualTo("ASSISTANT");
            assertThat(messages.get(2).get("content").asText()).isEqualTo("second");
            assertThat(messages.get(4).get("content").asText()).isEqualTo("third");
        }

        @Test
        void postingAMessageReturnsTheReply() throws Exception {
            String conversationId = createConversation();

            mvc.perform(authed(post("/api/conversations/" + conversationId + "/messages"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"content":"where is my order?"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("ASSISTANT"))
                    .andExpect(jsonPath("$.content").isNotEmpty());
        }

        @Test
        void rejectsAnEmptyMessage() throws Exception {
            String conversationId = createConversation();

            mvc.perform(authed(post("/api/conversations/" + conversationId + "/messages"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"content":"   "}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void postingToAnUnknownConversationIs404() throws Exception {
            mvc.perform(authed(post("/api/conversations/" + UUID.randomUUID() + "/messages"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"content":"hello"}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        void readingMessagesOfAnUnknownConversationIs404() throws Exception {
            mvc.perform(authed(get("/api/conversations/" + UUID.randomUUID() + "/messages")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void aFreshConversationHasNoMessages() throws Exception {
            String conversationId = createConversation();

            mvc.perform(authed(get("/api/conversations/" + conversationId + "/messages")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    class ListConversations {

        @Test
        void returnsAPageOfThisTenantsConversations() throws Exception {
            createConversation();
            createConversation();

            mvc.perform(authed(get("/api/conversations")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.page").value(0));
        }

        @Test
        void honoursTheRequestedPageSize() throws Exception {
            createConversation();
            createConversation();
            createConversation();

            mvc.perform(authed(get("/api/conversations").param("size", "2")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }

        @Test
        void newestFirst() throws Exception {
            String first = createConversation();
            String second = createConversation();

            String body = mvc.perform(authed(get("/api/conversations")))
                    .andReturn().getResponse().getContentAsString();

            JsonNode content = json.readTree(body).get("content");
            assertThat(content.get(0).get("id").asText()).isEqualTo(second);
            assertThat(content.get(1).get("id").asText()).isEqualTo(first);
        }

        @Test
        void anotherTenantsConversationsAreNotListed() throws Exception {
            createConversation();
            createConversation();

            token = registerTenant();

            mvc.perform(authed(get("/api/conversations")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void rejectsANegativePage() throws Exception {
            mvc.perform(authed(get("/api/conversations").param("page", "-1")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsAPageSizeAboveTheCap() throws Exception {
            mvc.perform(authed(get("/api/conversations").param("size", "500")))
                    .andExpect(status().isBadRequest());
        }
    }
}
