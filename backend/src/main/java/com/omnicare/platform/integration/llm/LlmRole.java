package com.omnicare.platform.integration.llm;

/**
 * Who is speaking in an {@link LlmMessage}.
 *
 * <p>A separate enum from the domain's {@code MessageRole} even though the
 * values overlap today. They answer to different masters: {@code MessageRole}
 * describes a transcript this product stores, while this one describes what a
 * model accepts. When a provider adds a role, only this side should have to move.
 */
public enum LlmRole {
    SYSTEM,
    USER,
    ASSISTANT
}
