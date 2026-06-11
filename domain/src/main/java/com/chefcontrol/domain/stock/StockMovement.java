package com.chefcontrol.domain.stock;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockMovement {

    private UUID id;
    private UUID restaurantId;
    private UUID productId;
    private MovementType type;
    private MovementDirection direction;
    private BigDecimal quantity;
    private UUID unitId;
    private BigDecimal costPerUnit;
    private BigDecimal stockBefore;
    private BigDecimal stockAfter;
    private UUID referenceId;
    private String referenceType;
    private UUID reversedBy;
    private UUID userId;
    private MovementSource source;
    private Instant createdAt;

    public static StockMovement forPurchase(UUID restaurantId, UUID productId,
                                            BigDecimal quantity, UUID unitId, BigDecimal costPerUnit,
                                            BigDecimal stockBefore, UUID referenceId, UUID userId) {
        return StockMovement.builder()
                .restaurantId(restaurantId)
                .productId(productId)
                .type(MovementType.PURCHASE)
                .direction(MovementDirection.IN)
                .quantity(quantity)
                .unitId(unitId)
                .costPerUnit(costPerUnit)
                .stockBefore(stockBefore)
                .stockAfter(stockBefore.add(quantity))
                .referenceId(referenceId)
                .referenceType("purchase_item")
                .userId(userId)
                .source(MovementSource.DASHBOARD)
                .build();
    }

    public static StockMovement forSale(UUID restaurantId, UUID productId,
                                        BigDecimal quantity, UUID unitId, BigDecimal avgCost,
                                        BigDecimal stockBefore, UUID referenceId, UUID userId) {
        return StockMovement.builder()
                .restaurantId(restaurantId)
                .productId(productId)
                .type(MovementType.SALE)
                .direction(MovementDirection.OUT)
                .quantity(quantity)
                .unitId(unitId)
                .costPerUnit(avgCost)
                .stockBefore(stockBefore)
                .stockAfter(stockBefore.subtract(quantity))
                .referenceId(referenceId)
                .referenceType("sale_item")
                .userId(userId)
                .source(MovementSource.DASHBOARD)
                .build();
    }

    public static StockMovement forWaste(UUID restaurantId, UUID productId,
                                         BigDecimal quantity, UUID unitId, BigDecimal costPerUnit,
                                         BigDecimal stockBefore, UUID referenceId, UUID userId) {
        return StockMovement.builder()
                .restaurantId(restaurantId)
                .productId(productId)
                .type(MovementType.WASTE)
                .direction(MovementDirection.OUT)
                .quantity(quantity)
                .unitId(unitId)
                .costPerUnit(costPerUnit)
                .stockBefore(stockBefore)
                .stockAfter(stockBefore.subtract(quantity))
                .referenceId(referenceId)
                .referenceType("waste_event")
                .userId(userId)
                .source(MovementSource.DASHBOARD)
                .build();
    }

    /**
     * Creates an adjustment movement from a physical stock count.
     * Derives direction and absolute quantity from the delta between current and counted stock.
     */
    public static StockMovement forAdjustment(UUID restaurantId, UUID productId,
                                               BigDecimal currentStock, BigDecimal countedQuantity,
                                               UUID unitId, UUID referenceId, UUID userId) {
        BigDecimal delta = countedQuantity.subtract(currentStock);
        MovementDirection direction = delta.compareTo(BigDecimal.ZERO) > 0
                ? MovementDirection.IN : MovementDirection.OUT;
        return StockMovement.builder()
                .restaurantId(restaurantId)
                .productId(productId)
                .type(MovementType.COUNT)
                .direction(direction)
                .quantity(delta.abs())
                .unitId(unitId)
                .stockBefore(currentStock)
                .stockAfter(countedQuantity)
                .referenceId(referenceId)
                .referenceType("stock_count")
                .userId(userId)
                .source(MovementSource.DASHBOARD)
                .build();
    }
}
