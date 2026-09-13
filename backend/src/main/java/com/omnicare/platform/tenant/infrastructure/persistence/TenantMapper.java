package com.omnicare.platform.tenant.infrastructure.persistence;

import com.omnicare.platform.shared.domain.Email;
import com.omnicare.platform.shared.domain.TenantId;
import com.omnicare.platform.shared.domain.UserId;
import com.omnicare.platform.tenant.domain.Tenant;
import com.omnicare.platform.tenant.domain.User;

/**
 * Translates between the tenant domain classes and their rows.
 *
 * <p>This class is the only place that knows both shapes, which is what keeps
 * the JPA annotations out of the domain and the domain's invariants out of the
 * ORM's reach.
 */
final class TenantMapper {

    private TenantMapper() {
    }

    static TenantEntity toEntity(Tenant tenant) {
        return new TenantEntity(
                tenant.id().value(),
                tenant.name(),
                tenant.plan(),
                tenant.createdAt(),
                tenant.updatedAt());
    }

    static Tenant toDomain(TenantEntity entity) {
        return Tenant.rehydrate(
                new TenantId(entity.getId()),
                entity.getName(),
                entity.getPlan(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static UserEntity toEntity(User user) {
        return new UserEntity(
                user.id().value(),
                user.tenantId().value(),
                user.email().value(),
                user.passwordHash(),
                user.role(),
                user.createdAt(),
                user.updatedAt());
    }

    static User toDomain(UserEntity entity) {
        return User.rehydrate(
                new UserId(entity.getId()),
                new TenantId(entity.getTenantId()),
                new Email(entity.getEmail()),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
