package com.omnicare.platform.conversation.api;

import com.omnicare.platform.conversation.domain.ConversationRepository;
import com.omnicare.platform.shared.domain.ConversationId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read access to a single conversation. Day 6 fills in the rest of this
 * resource; this much exists now because Day 5's isolation check is stated as an
 * HTTP one — tenant A must get a 404 for tenant B's conversation.
 */
@RestController
@RequestMapping("/api/conversations")
class ConversationController {

    private final ConversationRepository conversations;

    ConversationController(ConversationRepository conversations) {
        this.conversations = conversations;
    }

    /**
     * Note the deliberate absence of any {@code tenantId} check in this method.
     * There is nothing to check: row level security has already made another
     * tenant's conversation unreachable, so "belongs to someone else" and "does
     * not exist" arrive here as the same empty Optional — and leave as the same
     * 404, which is also what stops this endpoint confirming that an id exists.
     */
    @GetMapping("/{id}")
    ConversationResponse byId(@PathVariable UUID id) {
        return conversations.findById(new ConversationId(id))
                .map(ConversationResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
