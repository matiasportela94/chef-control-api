package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.domain.user.RoleName;
import com.chefcontrol.infrastructure.persistence.entity.RoleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaRoleRepository extends JpaRepository<RoleJpaEntity, UUID> {

    Optional<RoleJpaEntity> findByName(RoleName name);
}
