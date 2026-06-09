package com.chefcontrol.domain.repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface UnitConversionRepository {
    Optional<BigDecimal> findFactor(UUID fromUnitId, UUID toUnitId);
}
