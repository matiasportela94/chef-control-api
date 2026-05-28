package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.RoleRepository;
import com.chefcontrol.domain.user.RoleEntity;
import com.chefcontrol.domain.user.RoleName;
import com.chefcontrol.infrastructure.persistence.entity.RoleJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepository {

    private final JpaRoleRepository jpa;

    @Override
    public Optional<RoleEntity> findByName(RoleName name) {
        return jpa.findByName(name).map(RoleJpaEntity::toDomain);
    }
}
