package com.omnicare.platform.tenant.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code users}. Not exposed beyond this package. */
interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
