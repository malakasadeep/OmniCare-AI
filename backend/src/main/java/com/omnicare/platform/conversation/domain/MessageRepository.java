package com.omnicare.platform.conversation.domain;

import com.omnicare.platform.shared.domain.ConversationId;
import java.util.List;

/** Persistence contract for {@link Message}. */
public interface MessageRepository {

    Message save(Message message);

    /** The transcript, oldest first — the order the model must be shown. */
    List<Message> findByConversation(ConversationId conversationId);
}
