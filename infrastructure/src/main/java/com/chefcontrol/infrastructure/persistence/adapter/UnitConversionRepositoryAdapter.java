package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.UnitConversionRepository;
import com.chefcontrol.infrastructure.persistence.jpa.JpaUnitConversionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UnitConversionRepositoryAdapter implements UnitConversionRepository {

    private final JpaUnitConversionRepository jpa;

    @Override
    public Optional<BigDecimal> findFactor(UUID fromUnitId, UUID toUnitId) {
        return jpa.findByFromUnitIdAndToUnitId(fromUnitId, toUnitId)
                .map(e -> e.getFactor());
    }
}
