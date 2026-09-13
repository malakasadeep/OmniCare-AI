package com.omnicare.platform.tenant.api;

import java.util.UUID;

/** Who the caller is, as far as the current access token is concerned. */
public record MeResponse(UUID userId, UUID tenantId, String email, String role, String companyName) {
}
