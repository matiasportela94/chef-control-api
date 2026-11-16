package com.chefcontrol.domain.shared.valueobject;

import java.math.BigDecimal;

/** Neto e IVA de una alícuota, en un reporte de IVA (compras o ventas). */
public record IvaRateTotal(BigDecimal ivaPercentage, BigDecimal netAmount, BigDecimal ivaAmount) {

    public IvaRateTotal plus(IvaRateTotal other) {
        return new IvaRateTotal(ivaPercentage, netAmount.add(other.netAmount), ivaAmount.add(other.ivaAmount));
    }
}
