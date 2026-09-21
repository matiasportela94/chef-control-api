package com.chefcontrol.domain.product;

import com.chefcontrol.domain.alert.AlertSeverity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class Product {

    private UUID id;
    private UUID restaurantId;
    private UUID categoryId;
    private String categoryName;
    private String categoryColor;
    private String categoryIcon;
    private String name;
    private String sku;
    private UUID defaultUnitId;
    private String defaultUnitName;
    private String defaultUnitAbbreviation;
    private BigDecimal minStock;
    private BigDecimal maxStock;
    private BigDecimal yieldPercentage = HUNDRED;
    private boolean isActive = true;
    private Instant createdAt;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Cantidad bruta a descontar del stock para obtener {@code net} de producto utilizable.
     *
     * <p>El rendimiento es el estándar gastronómico: "la papa rinde 90%" significa que de 1 kg
     * comprado quedan 900 g útiles, así que para 1 kg útil hay que comprar 1/0,9 = 1,111 kg.
     * Se <b>divide</b> por el rendimiento; sumarle el 10% al neto daría 1,100 y no cierra.
     *
     * <p>Un rendimiento mayor a 100 es válido y devuelve menos que el neto: el arroz rinde ~250%
     * porque absorbe agua, y 300 g de arroz cocido salen de 120 g de arroz crudo.
     */
    public BigDecimal grossQuantityFor(BigDecimal net) {
        if (net == null || yieldPercentage == null || yieldPercentage.compareTo(HUNDRED) == 0) {
            return net;
        }
        return net.divide(yieldPercentage.divide(HUNDRED, 6, RoundingMode.HALF_UP), 3, RoundingMode.HALF_UP);
    }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean isLowStock(BigDecimal currentStock) {
        return minStock != null && currentStock.compareTo(minStock) < 0;
    }

    public boolean isOverstock(BigDecimal currentStock) {
        return maxStock != null && currentStock.compareTo(maxStock) > 0;
    }

    /**
     * CRITICAL when stock is below 50% of the minimum threshold, WARNING otherwise.
     * Only meaningful when {@link #isLowStock(BigDecimal)} is true.
     */
    public AlertSeverity lowStockSeverity(BigDecimal currentStock) {
        if (minStock != null && currentStock.compareTo(minStock.multiply(new BigDecimal("0.5"))) < 0) {
            return AlertSeverity.CRITICAL;
        }
        return AlertSeverity.WARNING;
    }
}
