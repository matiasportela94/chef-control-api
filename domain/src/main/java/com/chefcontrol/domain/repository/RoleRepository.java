package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.user.RoleEntity;
import com.chefcontrol.domain.user.RoleName;

import java.util.Optional;

public interface RoleRepository {

    Optional<RoleEntity> findByName(RoleName name);
}
