package com.chefcontrol.domain.sale;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Sale {

    private UUID id;
    private UUID restaurantId;
    private UUID userId;
    /** Lo cobrado, con el IVA adentro. */
    private BigDecimal totalAmount;
    private String source;
    private String notes;
    private Instant soldAt;
    private Instant createdAt;
    private SaleStatus status;
    /**
     * La alícuota que regía al vender, congelada como el precio ({@code Restaurant.salesIvaRate()}: 0 si no es RI).
     * Null = venta anterior al IVA: el precio entero es ingreso, y no se recalcula nunca.
     */
    private BigDecimal ivaPercentage;
    /** Σ neto de las líneas: el ingreso. Null si es anterior al IVA. */
    private BigDecimal netTotal;

    /**
     * Separa el IVA de cada línea al centavo y totaliza. El neto de la venta es la suma de los netos redondeados
     * (no el total dividido), así el ingreso total y el de cada plato cierran entre sí.
     */
    public void totalize(BigDecimal ivaPercentage, List<SaleItem> items) {
        items.forEach(i -> i.applyIva(ivaPercentage));
        this.ivaPercentage = ivaPercentage;
        this.totalAmount = items.stream().map(SaleItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.netTotal = items.stream().map(SaleItem::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean hasIvaData() {
        return ivaPercentage != null;
    }

    /** Lo que se le debe al fisco por esta venta. Null si es anterior al IVA. */
    public BigDecimal getIvaTotal() {
        return netTotal == null ? null : totalAmount.subtract(netTotal);
    }
}
