package com.chefcontrol.domain.stock;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una tanda producida de una preparación: consumió sus ingredientes (movimientos PRODUCTION OUT y su merma estándar)
 * y entró un lote (PRODUCTION IN). {@code saleItemId} / {@code parentProductionId}: la hizo el sistema porque una venta
 * u otra producción necesitaba la preparación y no había stock.
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Production {

    private UUID id;
    private UUID restaurantId;
    private UUID productId;
    private String productName;
    /** En la unidad por defecto de la preparación. */
    private BigDecimal quantity;
    private UUID unitId;
    private String unitName;
    private BigDecimal costPerUnit;
    private BigDecimal totalCost;
    private LocalDate expirationDate;
    private UUID saleItemId;
    /** La venta de {@code saleItemId}, para el detalle. */
    private UUID saleId;
    private UUID parentProductionId;
    @Builder.Default
    private ProductionStatus status = ProductionStatus.ACTIVE;
    private String notes;
    private UUID userId;
    private Instant producedAt;
    private Instant createdAt;

    public boolean isAutomatic() {
        return saleItemId != null || parentProductionId != null;
    }

    public boolean isReversed() {
        return status == ProductionStatus.REVERSED;
    }
}
