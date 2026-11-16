package com.chefcontrol.domain.shared.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

/** Alícuotas de IVA de Argentina. La base solo acota el rango: una alícuota nueva no pide migración. */
public final class IvaRates {

    private static final java.util.List<BigDecimal> RATES =
            Stream.of("0", "2.5", "5", "10.5", "21", "27").map(BigDecimal::new).toList();

    private IvaRates() {}

    /** Por {@code compareTo}: 10.5 y 10.50 son la misma alícuota. */
    public static boolean isValid(BigDecimal percentage) {
        return percentage != null && RATES.stream().anyMatch(r -> r.compareTo(percentage) == 0);
    }

    /** El neto de un importe con IVA, al centavo. El IVA es lo que falta: importe − neto, así los dos suman exacto. */
    public static BigDecimal netOf(BigDecimal amountWithIva, BigDecimal percentage) {
        return amountWithIva.divide(BigDecimal.ONE.add(percentage.movePointLeft(2)), 2, RoundingMode.HALF_UP);
    }
}
