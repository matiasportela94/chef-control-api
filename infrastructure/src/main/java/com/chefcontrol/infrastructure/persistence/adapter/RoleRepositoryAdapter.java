package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.RoleRepository;
import com.chefcontrol.domain.user.Role;
import com.chefcontrol.infrastructure.persistence.entity.RoleJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepository {

    private final JpaRoleRepository jpa;

    @Override
    public Role save(Role role) {
        return jpa.save(RoleJpaEntity.from(role)).toDomain();
    }

    @Override
    public Optional<Role> findById(UUID id) {
        return jpa.findById(id).map(RoleJpaEntity::toDomain);
    }

    @Override
    public Optional<Role> findByIdAndAccountId(UUID id, UUID accountId) {
        return jpa.findByIdAndAccountId(id, accountId).map(RoleJpaEntity::toDomain);
    }

    @Override
    public List<Role> findAllByAccountId(UUID accountId) {
        return jpa.findAllByAccountId(accountId).stream()
                .map(RoleJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Role> findByAccountIdAndIsSystemTrue(UUID accountId) {
        return jpa.findByAccountIdAndIsSystemTrue(accountId).map(RoleJpaEntity::toDomain);
    }

    @Override
    public boolean existsByAccountIdAndNameIgnoreCase(UUID accountId, String name) {
        return jpa.existsByAccountIdAndNameIgnoreCase(accountId, name);
    }

    @Override
    public void delete(UUID id) {
        jpa.deleteById(id);
    }
}
