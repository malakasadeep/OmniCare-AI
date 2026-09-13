package com.omnicare.platform.conversation.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code messages}. Not exposed beyond this package. */
interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
