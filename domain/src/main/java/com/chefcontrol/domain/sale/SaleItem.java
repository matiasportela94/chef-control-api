package com.chefcontrol.domain.sale;

import com.chefcontrol.domain.shared.valueobject.IvaRates;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Getter @Builder(toBuilder = true) @NoArgsConstructor @AllArgsConstructor
public class SaleItem {

    private UUID id;
    private UUID saleId;
    private UUID menuItemId;
    private String menuItemName;
    private int quantity;
    private BigDecimal unitPrice;
    /** Lo que entra como ingreso: el importe sin el IVA de la venta. Null = venta anterior al IVA. */
    private BigDecimal netAmount;

    /** Cantidad × precio, al centavo: lo que se cobró por la línea, con el IVA adentro. */
    public BigDecimal amount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    void applyIva(BigDecimal ivaPercentage) {
        netAmount = IvaRates.netOf(amount(), ivaPercentage);
    }
}
