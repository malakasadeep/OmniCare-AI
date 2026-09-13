package com.omnicare.platform.conversation.api;

import com.omnicare.platform.conversation.application.ConversationService;
import com.omnicare.platform.conversation.domain.Conversation;
import com.omnicare.platform.conversation.domain.ConversationRepository;
import com.omnicare.platform.shared.api.ResourceNotFoundException;
import com.omnicare.platform.shared.domain.ConversationId;
import com.omnicare.platform.shared.domain.VisitorId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The conversation resource.
 *
 * <p>Controllers here do three things and no more: bind and validate the
 * request, call one service method, and map the result to a DTO. Domain objects
 * never leave this layer — returning a {@code Conversation} would tie the wire
 * format to the domain, so a rename in the domain would break every client.
 */
@RestController
@RequestMapping("/api/conversations")
@Validated
class ConversationController {

    /** Above this, one request could pull a tenant's whole history into memory. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ConversationService conversations;
    private final ConversationRepository repository;

    ConversationController(ConversationService conversations, ConversationRepository repository) {
        this.conversations = conversations;
        this.repository = repository;
    }

    @PostMapping
    ResponseEntity<ConversationResponse> create(@RequestBody(required = false)
                                                CreateConversationRequest request) {
        UUID visitorId = request == null ? null : request.visitorId();
        Conversation conversation = conversations.start(
                visitorId == null ? VisitorId.generate() : new VisitorId(visitorId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationResponse.from(conversation));
    }

    @GetMapping
    PageResponse<ConversationResponse> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must not be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "size must be at most " + MAX_PAGE_SIZE)
            int size) {

        List<ConversationResponse> content = conversations.list(page, size).stream()
                .map(ConversationResponse::from)
                .toList();
        return PageResponse.of(content, page, size, conversations.count());
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
        return repository.findById(new ConversationId(id))
                .map(ConversationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation"));
    }

    @GetMapping("/{id}/messages")
    List<MessageResponse> messages(@PathVariable UUID id) {
        return conversations.transcript(new ConversationId(id)).stream()
                .map(MessageResponse::from)
                .toList();
    }

    @PostMapping("/{id}/messages")
    ResponseEntity<MessageResponse> postMessage(@PathVariable UUID id,
                                                @Valid @RequestBody PostMessageRequest request) {
        MessageResponse reply = MessageResponse.from(
                conversations.postMessage(new ConversationId(id), request.content()));
        return ResponseEntity.status(HttpStatus.CREATED).body(reply);
    }
}
