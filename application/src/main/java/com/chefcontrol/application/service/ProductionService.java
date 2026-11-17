package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.application.service.StockConsumptionService.ProductionOrder;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.ProductionRepository;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.MovementDirection;
import com.chefcontrol.domain.stock.MovementSource;
import com.chefcontrol.domain.stock.MovementType;
import com.chefcontrol.domain.stock.Production;
import com.chefcontrol.domain.stock.ProductionStatus;
import com.chefcontrol.domain.stock.StockMovement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registrar, listar y revertir producciones de preparaciones. La mecánica (consumir, producir lo que falte, el lote)
 * vive en {@link StockConsumptionService}; acá están los casos de uso.
 */
@Service
@RequiredArgsConstructor
public class ProductionService {

    private final ProductionRepository productionRepository;
    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockBatchService stockBatchService;
    private final StockConsumptionService stockConsumptionService;
    private final UnitConversionService unitConversionService;
    private final com.chefcontrol.domain.repository.UnitRepository unitRepository;
    private final AlertEvaluationService alertEvaluationService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    /**
     * "Hice 5 kg de mayonesa base": descuenta los ingredientes FIFO según la receta escalada y entra un lote al costo de
     * lo consumido. Si un ingrediente es otra preparación sin stock, se produce lo que falta (Mati, 2026-11-17: la misma
     * regla que al vender).
     */
    @Transactional
    public Production recordProduction(RecordProductionCommand cmd) {
        UUID restaurantId = TenantContext.require();
        Product product = productRepository.findByIdAndRestaurantId(cmd.productId(), restaurantId)
                .filter(Product::isActive)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "Product not found"));
        Recipe recipe = recipeRepository.findByProductIdAndRestaurantId(product.getId(), restaurantId)
                .orElseThrow(() -> AppException.badRequest(ErrorCode.PRODUCT_NOT_A_PREPARATION,
                        "The product has no preparation recipe: " + product.getName()));
        BigDecimal quantity = unitConversionService.convert(cmd.quantity(), cmd.unitId(), product.getDefaultUnitId())
                .setScale(3, RoundingMode.HALF_UP);
        if (quantity.signum() <= 0) {
            throw AppException.badRequest(ErrorCode.VALIDATION_ERROR, "The quantity must be greater than zero");
        }
        List<String> missing = new ArrayList<>();
        stockConsumptionService.collectMissingPurchaseCosts(recipe.getItems(), restaurantId, missing);
        if (!missing.isEmpty()) {
            throw AppException.badRequest(ErrorCode.MISSING_PURCHASE_COST,
                    "Sin historial de compras para: " + String.join(", ", missing));
        }
        return stockConsumptionService.produce(new ProductionOrder(restaurantId, currentUserProvider.currentUserId(),
                product, recipe, quantity, cmd.expirationDate(), cmd.producedAt(), cmd.notes(), null, null),
                new HashSet<>());
    }

    /**
     * Revierte la producción con la semántica de {@code reversePurchase}: lo producido sale entero (el stock de la
     * preparación puede quedar negativo si ya se usó; lo que se vendió conserva su costo), el lote queda en cero, y los
     * ingredientes vuelven a los lotes exactos de donde salieron. Las producciones automáticas que hizo esta se
     * revierten con ella. Una automática se puede revertir sola: es el remedio cuando después cargan la producción a mano.
     */
    @Transactional
    public Production reverseProduction(UUID productionId) {
        UUID restaurantId = TenantContext.require();
        Production production = productionRepository.findByIdAndRestaurantId(productionId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCTION_NOT_FOUND, "Production not found"));
        if (production.isReversed()) {
            throw AppException.conflict(ErrorCode.PRODUCTION_ALREADY_REVERSED, "Production already reversed");
        }
        reverse(production, currentUserProvider.currentUserId());
        auditService.log(AuditAction.PRODUCTION_REVERSED, "Production", production.getId(),
                Map.of("productId", production.getProductId(), "automatic", production.isAutomatic()));
        return productionRepository.findByIdAndRestaurantId(productionId, restaurantId).orElseThrow();
    }

    /**
     * Las automáticas de un ítem de venta, la más nueva primero. Lo llama {@code reverseSale} DESPUÉS de revertir los
     * movimientos de la venta: así el consumo de la preparación vuelve a su lote y recién después el lote queda en cero.
     */
    public void reverseAutomaticProductionsOf(UUID saleItemId, UUID userId) {
        for (Production production : productionRepository.findBySaleItemIdNewestFirst(saleItemId)) {
            if (!production.isReversed()) reverse(production, userId);
        }
    }

    private void reverse(Production production, UUID userId) {
        UUID restaurantId = production.getRestaurantId();
        for (StockMovement original : stockMovementRepository
                .findByReferenceIdAndReferenceType(production.getId(), "production")) {
            if (original.getType() == MovementType.REVERSAL || original.getReversedBy() != null) continue;

            boolean wasIn = original.getDirection() == MovementDirection.IN;
            BigDecimal stockBefore = stockMovementRepository.getCurrentStock(original.getProductId(), restaurantId);
            StockMovement reversal = stockMovementRepository.save(StockMovement.builder()
                    .restaurantId(restaurantId)
                    .productId(original.getProductId())
                    .type(MovementType.REVERSAL)
                    .direction(wasIn ? MovementDirection.OUT : MovementDirection.IN)
                    .quantity(original.getQuantity())
                    .unitId(original.getUnitId())
                    .costPerUnit(original.getCostPerUnit())
                    .stockBefore(stockBefore)
                    .stockAfter(wasIn ? stockBefore.subtract(original.getQuantity())
                            : stockBefore.add(original.getQuantity()))
                    .referenceId(original.getId())
                    .referenceType("reversal")
                    .userId(userId)
                    .source(MovementSource.DASHBOARD)
                    .build());
            stockMovementRepository.markReversed(original.getId(), reversal.getId());
            if (wasIn) {
                // Como la compra: el lote que creó esta producción deja de existir.
                stockBatchRepository.zeroQuantityRemainingByProductionId(production.getId());
            } else {
                stockBatchService.reverseAllocations(restaurantId, original.getId(), reversal.getId());
            }
            alertEvaluationService.evaluate(original.getProductId(), restaurantId, reversal.getStockAfter());
        }
        for (Production child : productionRepository.findByParentProductionIdNewestFirst(production.getId())) {
            if (!child.isReversed()) reverse(child, userId);
        }
        production.setStatus(ProductionStatus.REVERSED);
        productionRepository.save(production);
    }

    public Page<Production> listProductions(UUID productId, Boolean automatic, Instant from, Instant to,
                                            PageRequest pageRequest) {
        return productionRepository.findByRestaurant(TenantContext.require(), productId, automatic, from, to, pageRequest);
    }

    public Production getProduction(UUID productionId) {
        return productionRepository.findByIdAndRestaurantId(productionId, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCTION_NOT_FOUND, "Production not found"));
    }

    /**
     * Lo que consumió la producción, por ingrediente: el consumo y su merma estándar (los dos al mismo costo unitario).
     * {@code quantity} es el bruto (consumo + merma) en la unidad del insumo.
     */
    public List<ProductionIngredient> getProductionIngredients(UUID productionId) {
        Production production = getProduction(productionId);
        Map<UUID, ProductionIngredient> byProduct = new LinkedHashMap<>();
        for (StockMovement m : stockMovementRepository.findByReferenceIdAndReferenceType(production.getId(), "production")) {
            if (m.getDirection() != MovementDirection.OUT) continue;
            BigDecimal waste = m.getType() == MovementType.WASTE ? m.getQuantity() : BigDecimal.ZERO;
            byProduct.merge(m.getProductId(),
                    new ProductionIngredient(m.getProductId(), null, m.getQuantity(), m.getUnitId(), null, waste,
                            m.getCostPerUnit()),
                    (a, b) -> new ProductionIngredient(a.productId(), null, a.quantity().add(b.quantity()), a.unitId(),
                            null, a.standardWaste().add(b.standardWaste()), a.costPerUnit()));
        }
        return byProduct.values().stream().map(i -> new ProductionIngredient(i.productId(),
                productRepository.findByIdAndRestaurantId(i.productId(), production.getRestaurantId())
                        .map(Product::getName).orElse(null),
                i.quantity(), i.unitId(), unitRepository.findById(i.unitId()).map(u -> u.getName()).orElse(null),
                i.standardWaste(), i.costPerUnit())).toList();
    }

    public record RecordProductionCommand(UUID productId, BigDecimal quantity, UUID unitId, LocalDate expirationDate,
                                          Instant producedAt, String notes) {}

    public record ProductionIngredient(UUID productId, String productName, BigDecimal quantity, UUID unitId,
                                       String unitName, BigDecimal standardWaste, BigDecimal costPerUnit) {
        public BigDecimal totalCost() {
            return costPerUnit == null ? null : quantity.multiply(costPerUnit).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
