package com.chefcontrol.domain.purchase;

import com.chefcontrol.domain.restaurant.TaxCondition;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Purchase {

    private UUID id;
    private UUID restaurantId;
    private UUID supplierId;
    private String supplierName;
    private UUID userId;
    /** Lo que dice la factura: neto + IVA. En una compra anterior al IVA, cantidad × precio. */
    private BigDecimal total;
    private String notes;
    private Instant purchasedAt;
    private Instant createdAt;
    private PurchaseStatus status;
    /** Null = compra anterior al IVA (V32): no tiene neto ni IVA y no se recalcula. */
    private Boolean pricesIncludeIva;
    /** El comprobante, si se cargó desde una factura o remito: para avisar si ya está cargado. */
    private InvoiceType invoiceType;
    private String invoiceNumber;
    private BigDecimal netTotal;
    private BigDecimal ivaTotal;

    /** Lo pedido, o el default de la condición del restaurante (Factura A para el RI, B para el resto). */
    public static boolean pricesIncludeIvaOrDefault(Boolean requested, TaxCondition condition) {
        return requested != null ? requested : TaxCondition.pricesIncludeIvaByDefault(condition);
    }

    public boolean hasIvaData() {
        return pricesIncludeIva != null;
    }

    /**
     * Totales a partir de las líneas, ya con su IVA aplicado ({@link PurchaseItem#applyIva}). Se suman los montos
     * redondeados de cada línea, así que neto + IVA de la compra es exactamente la suma de sus líneas.
     */
    public void totalize(List<PurchaseItem> items) {
        if (!hasIvaData()) {
            total = items.stream().map(i -> i.getPricePerUnit().multiply(i.getQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            return;
        }
        netTotal = items.stream().map(PurchaseItem::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        ivaTotal = items.stream().map(PurchaseItem::getIvaAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        total = netTotal.add(ivaTotal);
    }
}
