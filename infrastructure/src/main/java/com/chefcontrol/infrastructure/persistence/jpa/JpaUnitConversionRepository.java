package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.UnitConversionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaUnitConversionRepository extends JpaRepository<UnitConversionJpaEntity, UUID> {
    Optional<UnitConversionJpaEntity> findByFromUnitIdAndToUnitId(UUID fromUnitId, UUID toUnitId);
}
