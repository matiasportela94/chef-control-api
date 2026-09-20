package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.RoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaRoleRepository extends JpaRepository<RoleJpaEntity, UUID> {

    Optional<RoleJpaEntity> findByIdAndAccountId(UUID id, UUID accountId);

    List<RoleJpaEntity> findAllByAccountId(UUID accountId);

    Optional<RoleJpaEntity> findByAccountIdAndIsSystemTrue(UUID accountId);

    boolean existsByAccountIdAndNameIgnoreCase(UUID accountId, String name);
}
