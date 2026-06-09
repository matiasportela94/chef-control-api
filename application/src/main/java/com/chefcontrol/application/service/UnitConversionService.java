package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.repository.UnitConversionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitConversionService {

    private final UnitConversionRepository unitConversionRepository;

    /**
     * Converts a quantity from one unit to another using the unit_conversions table.
     * Returns qty unchanged if fromUnitId == toUnitId.
     */
    public BigDecimal convert(BigDecimal qty, UUID fromUnitId, UUID toUnitId) {
        if (fromUnitId.equals(toUnitId)) return qty;
        BigDecimal factor = unitConversionRepository.findFactor(fromUnitId, toUnitId)
                .orElseThrow(() -> AppException.badRequest(ErrorCode.UNIT_CONVERSION_NOT_FOUND,
                        "No conversion path from unit " + fromUnitId + " to " + toUnitId));
        return qty.multiply(factor).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Converts a price-per-unit inversely when the quantity unit changes.
     * If qty grows by factor F, price-per-unit shrinks by F to keep the total constant.
     */
    public BigDecimal convertPrice(BigDecimal pricePerUnit, UUID fromUnitId, UUID toUnitId) {
        if (fromUnitId.equals(toUnitId)) return pricePerUnit;
        BigDecimal factor = unitConversionRepository.findFactor(fromUnitId, toUnitId)
                .orElseThrow(() -> AppException.badRequest(ErrorCode.UNIT_CONVERSION_NOT_FOUND,
                        "No conversion path from unit " + fromUnitId + " to " + toUnitId));
        return pricePerUnit.divide(factor, 6, RoundingMode.HALF_UP);
    }
}
