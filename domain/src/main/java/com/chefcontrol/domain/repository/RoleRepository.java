package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.user.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {

    Role save(Role role);

    /** Sin filtro de cuenta — para resolver un roleId ya conocido y confiable (ej. desde una membership). */
    Optional<Role> findById(UUID id);

    Optional<Role> findByIdAndAccountId(UUID id, UUID accountId);

    List<Role> findAllByAccountId(UUID accountId);

    Optional<Role> findByAccountIdAndIsSystemTrue(UUID accountId);

    boolean existsByAccountIdAndNameIgnoreCase(UUID accountId, String name);

    void delete(UUID id);
}
