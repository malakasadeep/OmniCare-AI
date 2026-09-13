package com.omnicare.platform.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One turn from the visitor. */
public record PostMessageRequest(

        @NotBlank(message = "content is required")
        @Size(max = 8000, message = "content must be at most 8000 characters")
        String content) {
}
