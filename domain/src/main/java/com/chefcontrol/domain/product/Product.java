package com.chefcontrol.domain.product;

import com.chefcontrol.domain.alert.AlertSeverity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class Product {

    private UUID id;
    private UUID restaurantId;
    private UUID categoryId;
    private String categoryName;
    private String categoryColor;
    private String name;
    private String sku;
    private UUID defaultUnitId;
    private String defaultUnitName;
    private String defaultUnitAbbreviation;
    private BigDecimal minStock;
    private BigDecimal maxStock;
    private boolean isActive = true;
    private Instant createdAt;

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
