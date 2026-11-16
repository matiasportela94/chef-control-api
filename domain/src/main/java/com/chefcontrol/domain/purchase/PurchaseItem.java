package com.chefcontrol.domain.purchase;

import com.chefcontrol.domain.restaurant.TaxCondition;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PurchaseItem {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private UUID id;
    private UUID purchaseId;
    private UUID productId;
    private String productName;
    private String productSku;
    private BigDecimal quantity;
    private UUID unitId;
    private String unitName;
    private String unitAbbreviation;
    /** El precio tal como se cargó: con o sin IVA según {@link Purchase#getPricesIncludeIva()}. */
    private BigDecimal pricePerUnit;
    private Instant createdAt;
    /** Del lote que creó esta línea, igual que {@link #quantityRemaining}: no es columna de purchase_items. */
    private BigDecimal quantityRemaining;
    private LocalDate expirationDate;
    /** Null en las líneas anteriores al IVA (V32), igual que el neto y el IVA. */
    private BigDecimal ivaPercentage;
    private BigDecimal netAmount;
    private BigDecimal ivaAmount;

    /**
     * La tasa de la línea: la pedida, si no la del insumo, si no 0. Un 0 explícito es "sin IVA" aunque el insumo
     * tenga tasa.
     */
    public static BigDecimal ivaPercentageOrDefault(BigDecimal requested, BigDecimal productRate) {
        if (requested != null) return requested;
        return productRate != null ? productRate : BigDecimal.ZERO;
    }

    /**
     * Fija tasa, neto e IVA de la línea, en plata (2 decimales). Con el IVA adentro, el IVA es lo que falta para
     * llegar al importe de la línea: neto + IVA da siempre ese importe y no se pierde un centavo.
     */
    public void applyIva(boolean pricesIncludeIva, BigDecimal percentage) {
        BigDecimal amount = pricePerUnit.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = percentage.divide(HUNDRED);
        if (pricesIncludeIva) {
            netAmount = amount.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
            ivaAmount = amount.subtract(netAmount);
        } else {
            netAmount = amount;
            ivaAmount = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }
        ivaPercentage = percentage;
    }

    /**
     * Costo por unidad que entra al stock (movimiento y lote), y de ahí al food cost. Para el RI es el neto: el IVA
     * lo recupera. Para el monotributista (y sin condición cargada) es neto + IVA. Sale del precio unitario y no del
     * neto de la línea, que está redondeado a centavos: con precios con IVA y sin recuperarlo es el precio de
     * siempre, sin un decimal de diferencia.
     */
    public BigDecimal stockCostPerUnit(boolean pricesIncludeIva, TaxCondition condition) {
        if (ivaPercentage == null || pricesIncludeIva == TaxCondition.ivaIsCost(condition)) return pricePerUnit;
        BigDecimal factor = BigDecimal.ONE.add(ivaPercentage.divide(HUNDRED));
        return pricesIncludeIva
                ? pricePerUnit.divide(factor, 6, RoundingMode.HALF_UP)
                : pricePerUnit.multiply(factor).setScale(6, RoundingMode.HALF_UP);
    }
}
