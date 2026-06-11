package com.chefcontrol.api.product.dto;

import com.chefcontrol.domain.product.Unit;

import java.math.BigDecimal;
import java.util.UUID;

public record UnitResponse(
        UUID id,
        String name,
        String abbreviation,
        String type,
        UUID baseUnitId,
        BigDecimal toBaseFactor
) {
    public static UnitResponse from(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getAbbreviation(),
                unit.getType().name(),
                unit.getBaseUnitId(),
                unit.getToBaseFactor());
    }
}
