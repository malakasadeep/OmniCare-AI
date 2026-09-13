package com.omnicare.platform.tenant.domain;

import com.omnicare.platform.shared.domain.Email;
import com.omnicare.platform.shared.domain.Guards;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * A person who signs in to the dashboard — an owner or an agent. Visitors
 * talking to the widget are not users; they are identified by a
 * {@code VisitorId} instead.
 *
 * <p>Only the hash of a password ever reaches this class. Hashing and
 * verification belong to the auth layer, which knows about BCrypt; the domain
 * only knows that it is holding an opaque, non-blank secret.
 */
public final class User {

    private final UserId id;
    private final TenantId tenantId;
    private final Email email;
    private final UserRole role;
    private final Instant createdAt;

    private String passwordHash;
    private Instant updatedAt;

    private User(UserId id,
                 TenantId tenantId,
                 Email email,
                 String passwordHash,
                 UserRole role,
                 Instant createdAt,
                 Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static User register(TenantId tenantId,
                                Email email,
                                String passwordHash,
                                UserRole role,
                                Instant registeredAt) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(registeredAt, "registeredAt must not be null");
        return new User(
                UserId.generate(),
                tenantId,
                email,
                Guards.requireNonBlank(passwordHash, "passwordHash"),
                role,
                registeredAt,
                registeredAt);
    }

    /**
     * Rebuilds a user from state that was already persisted. For the
     * persistence mappers only — see {@code Conversation.rehydrate}.
     */
    public static User rehydrate(UserId id,
                                 TenantId tenantId,
                                 Email email,
                                 String passwordHash,
                                 UserRole role,
                                 Instant createdAt,
                                 Instant updatedAt) {
        return new User(id, tenantId, email, passwordHash, role, createdAt, updatedAt);
    }

    public void changePassword(String newPasswordHash, Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.passwordHash = Guards.requireNonBlank(newPasswordHash, "newPasswordHash");
        this.updatedAt = occurredAt;
    }

    public boolean isOwner() {
        return role == UserRole.OWNER;
    }

    public UserId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public Email email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserRole role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Deliberately omits the password hash — this string ends up in logs. */
    @Override
    public String toString() {
        return "User[id=%s, tenant=%s, email=%s, role=%s]".formatted(
                id.value(), tenantId.value(), email.value(), role);
    }
}
