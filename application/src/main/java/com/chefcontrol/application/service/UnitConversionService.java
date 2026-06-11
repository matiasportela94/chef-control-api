package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.UnitConversionRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitConversionService {

    private final UnitConversionRepository unitConversionRepository;
    private final UnitRepository unitRepository;

    /**
     * Converts a quantity from one unit to another.
     * Uses to_base_factor when both units share a base; falls back to unit_conversions table.
     */
    public BigDecimal convert(BigDecimal qty, UUID fromUnitId, UUID toUnitId) {
        if (fromUnitId.equals(toUnitId)) return qty;
        BigDecimal factor = resolveFactor(fromUnitId, toUnitId);
        return qty.multiply(factor).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Converts a price-per-unit inversely: if qty grows by factor F, price shrinks by F.
     */
    public BigDecimal convertPrice(BigDecimal pricePerUnit, UUID fromUnitId, UUID toUnitId) {
        if (fromUnitId.equals(toUnitId)) return pricePerUnit;
        BigDecimal factor = resolveFactor(fromUnitId, toUnitId);
        return pricePerUnit.divide(factor, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveFactor(UUID fromUnitId, UUID toUnitId) {
        Optional<Unit> fromOpt = unitRepository.findById(fromUnitId);
        Optional<Unit> toOpt   = unitRepository.findById(toUnitId);

        if (fromOpt.isPresent() && toOpt.isPresent()) {
            Unit from = fromOpt.get();
            Unit to   = toOpt.get();
            if (from.getToBaseFactor() != null && to.getToBaseFactor() != null
                    && from.getType() == to.getType()) {
                return from.getToBaseFactor().divide(to.getToBaseFactor(), 10, RoundingMode.HALF_UP);
            }
        }

        return unitConversionRepository.findFactor(fromUnitId, toUnitId)
                .orElseThrow(() -> AppException.badRequest(ErrorCode.UNIT_CONVERSION_NOT_FOUND,
                        "No conversion path from unit " + fromUnitId + " to " + toUnitId));
    }
}
