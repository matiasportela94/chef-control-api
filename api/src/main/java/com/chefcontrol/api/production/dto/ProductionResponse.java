package com.chefcontrol.api.production.dto;

import com.chefcontrol.application.service.ProductionService.ProductionIngredient;
import com.chefcontrol.domain.stock.Production;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code automatic}: la hizo el sistema porque una venta ({@code saleId}) u otra producción ({@code parentProductionId})
 * necesitaba la preparación sin stock. {@code ingredients} solo en el detalle (null en el listado).
 */
public record ProductionResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal quantity,
        UUID unitId,
        String unitName,
        BigDecimal costPerUnit,
        BigDecimal totalCost,
        LocalDate expirationDate,
        Instant producedAt,
        boolean automatic,
        UUID saleId,
        UUID parentProductionId,
        String status,
        String notes,
        List<Ingredient> ingredients
) {
    /** {@code quantity}: el bruto consumido (incluye {@code standardWaste}), en la unidad del insumo. */
    public record Ingredient(UUID productId, String productName, BigDecimal quantity, UUID unitId, String unitName,
                             BigDecimal standardWaste, BigDecimal costPerUnit, BigDecimal totalCost) {}

    public static ProductionResponse from(Production p, List<ProductionIngredient> ingredients) {
        return new ProductionResponse(p.getId(), p.getProductId(), p.getProductName(), p.getQuantity(), p.getUnitId(),
                p.getUnitName(), p.getCostPerUnit(), p.getTotalCost(), p.getExpirationDate(), p.getProducedAt(),
                p.isAutomatic(), p.getSaleId(), p.getParentProductionId(), p.getStatus().name(), p.getNotes(),
                ingredients == null ? null : ingredients.stream().map(i -> new Ingredient(i.productId(), i.productName(),
                        i.quantity(), i.unitId(), i.unitName(), i.standardWaste(), i.costPerUnit(), i.totalCost()))
                        .toList());
    }
}
