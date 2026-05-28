package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.infrastructure.persistence.entity.UnitJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UnitRepositoryAdapter implements UnitRepository {

    private final JpaUnitRepository jpa;

    @Override
    public List<Unit> findAll() {
        return jpa.findAll().stream()
                .map(UnitJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Unit> findById(UUID id) {
        return jpa.findById(id).map(UnitJpaEntity::toDomain);
    }
}
