package com.chefcontrol.api.product.dto;

import com.chefcontrol.domain.product.Unit;

import java.util.UUID;

public record UnitResponse(
        UUID id,
        String name,
        String abbreviation,
        String type
) {
    public static UnitResponse from(Unit unit) {
        return new UnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getAbbreviation(),
                unit.getType().name());
    }
}
