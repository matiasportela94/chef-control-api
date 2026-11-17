package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.menu.Recipe;
import com.chefcontrol.domain.menu.RecipeIngredient;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.ProductionRepository;
import com.chefcontrol.domain.repository.RecipeRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.stock.Production;
import com.chefcontrol.domain.stock.StockMovement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Descontar un insumo del stock por una receta, para una venta o para una producción. Es el único lugar con la regla:
 * el bruto según el rendimiento, partido en el consumo y la merma estándar; el costo FIFO de lo que se toma; y si el
 * insumo es una preparación sin stock suficiente, producir lo que falta en ese momento (recursivo).
 *
 * <p>Corre dentro de la transacción de quien lo llama (la venta o la producción): sin {@code @Transactional} propio.
 */
@Service
@RequiredArgsConstructor
public class StockConsumptionService {

    private final ProductRepository productRepository;
    private final RecipeRepository recipeRepository;
    private final ProductionRepository productionRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockBatchService stockBatchService;
    private final UnitConversionService unitConversionService;
    private final AlertEvaluationService alertEvaluationService;
    private final FoodCostService foodCostService;
    private final AuditService auditService;

    /**
     * Para qué se consume: una venta (los movimientos cuelgan del sale_item) o una producción (cuelgan de ella).
     * {@code at} es la fecha que lleva una producción automática que haga falta: la de la venta o la de la producción.
     */
    public record Consumer(UUID restaurantId, UUID userId, UUID saleItemId, UUID productionId, Instant at) {
        public static Consumer forSale(UUID restaurantId, UUID userId, UUID saleItemId, Instant soldAt) {
            return new Consumer(restaurantId, userId, saleItemId, null, soldAt);
        }

        static Consumer forProduction(UUID restaurantId, UUID userId, Production production) {
            return new Consumer(restaurantId, userId, null, production.getId(), production.getProducedAt());
        }

        boolean isSale() {
            return saleItemId != null;
        }
    }

    /** Lo que se quiere producir; {@code quantity} en la unidad por defecto de la preparación. */
    public record ProductionOrder(UUID restaurantId, UUID userId, Product preparation, Recipe recipe,
                                  BigDecimal quantity, LocalDate expirationDate, Instant producedAt, String notes,
                                  UUID saleItemId, UUID parentProductionId) {}

    /**
     * Descuenta {@code quantity} (en {@code unitId}, lo que dice la receta) del insumo. Devuelve el costo de lo
     * consumido: el bruto al costo FIFO.
     *
     * @param producing las preparaciones que se están produciendo en este camino: la guarda contra un ciclo
     */
    public BigDecimal consume(Consumer consumer, UUID productId, BigDecimal quantity, UUID unitId, Set<UUID> producing) {
        UUID restaurantId = consumer.restaurantId();
        Product product = productRepository.findByIdAndRestaurantId(productId, restaurantId).orElse(null);
        UUID defaultUnitId = product != null ? product.getDefaultUnitId() : unitId;

        // El neto se redondea a la precisión con la que se guarda ANTES de restar, para que consumo + merma dé
        // exactamente el bruto y no quede medio gramo colgado.
        BigDecimal netQuantity = unitConversionService.convert(quantity, unitId, defaultUnitId)
                .setScale(3, RoundingMode.HALF_UP);
        BigDecimal grossQuantity = (product != null ? product.grossQuantityFor(netQuantity) : netQuantity)
                .setScale(3, RoundingMode.HALF_UP);

        // Una preparación sin stock suficiente se produce en el momento por lo que falta (Mati, 2026-11-17). Si después
        // cargan esa producción a mano, se descuenta dos veces: el remedio es revertir la automática.
        Optional<Recipe> preparation = recipeRepository.findByProductIdAndRestaurantId(productId, restaurantId);
        if (preparation.isPresent() && product != null) {
            BigDecimal stock = stockMovementRepository.getCurrentStock(productId, restaurantId);
            BigDecimal missing = grossQuantity.subtract(stock.max(BigDecimal.ZERO));
            if (missing.signum() > 0) {
                produce(new ProductionOrder(restaurantId, consumer.userId(), product, preparation.get(), missing,
                        null, consumer.at(), null, consumer.saleItemId(), consumer.productionId()), producing);
            }
        }

        // El costo real de lo consumido: lo que el bruto toma de los lotes FIFO, a lo que costó cada uno; lo que no
        // cubran, a la última compra (de una preparación, a lo que cuesta hacerla). Uno solo para el consumo y la merma
        // estándar: partirlo por lote haría arbitrario cuánto es costo y cuánto merma.
        BigDecimal fallback = preparation.isPresent()
                ? foodCostService.theoreticalUnitCost(productId, restaurantId, null)
                : stockMovementRepository.findLastPurchaseCostPerUnit(productId, restaurantId);
        BigDecimal unitCost = stockBatchService.fifoUnitCost(restaurantId, productId, grossQuantity, fallback);

        // Con rendimiento > 100 (el arroz absorbe agua) el bruto es MENOR que el neto: no hay merma, y el consumo va
        // por el bruto.
        BigDecimal used = netQuantity.min(grossQuantity);
        BigDecimal yieldWaste = grossQuantity.subtract(netQuantity);

        StockMovement movement = record(consumer.isSale()
                ? StockMovement.forSale(restaurantId, productId, used, defaultUnitId, unitCost,
                        stockMovementRepository.getCurrentStock(productId, restaurantId), consumer.saleItemId(),
                        consumer.userId())
                : StockMovement.forProductionUse(restaurantId, productId, used, defaultUnitId, unitCost,
                        stockMovementRepository.getCurrentStock(productId, restaurantId), consumer.productionId(),
                        consumer.userId()));
        if (yieldWaste.signum() > 0) {
            movement = record(consumer.isSale()
                    ? StockMovement.forStandardYieldWaste(restaurantId, productId, yieldWaste, defaultUnitId, unitCost,
                            stockMovementRepository.getCurrentStock(productId, restaurantId), consumer.saleItemId(),
                            consumer.userId())
                    : StockMovement.forProductionYieldWaste(restaurantId, productId, yieldWaste, defaultUnitId,
                            unitCost, stockMovementRepository.getCurrentStock(productId, restaurantId),
                            consumer.productionId(), consumer.userId()));
        }
        alertEvaluationService.evaluate(productId, restaurantId, movement.getStockAfter());
        return grossQuantity.multiply(unitCost);
    }

    /**
     * Produce una tanda: consume los ingredientes de la receta escalada (cada uno por {@link #consume}, que a su vez
     * produce lo que falte), entra el lote de la preparación al costo de lo consumido y queda la producción.
     */
    public Production produce(ProductionOrder order, Set<UUID> producing) {
        Product preparation = order.preparation();
        if (!producing.add(preparation.getId())) {
            // Se rechaza al guardar la receta; si igual llega (datos viejos), cortar antes de recursar para siempre.
            throw AppException.badRequest(ErrorCode.PREPARATION_CYCLE,
                    "The preparation uses itself: " + preparation.getName());
        }
        UUID restaurantId = order.restaurantId();
        BigDecimal quantity = order.quantity().setScale(3, RoundingMode.HALF_UP);

        Production production = productionRepository.save(Production.builder()
                .restaurantId(restaurantId)
                .productId(preparation.getId())
                .quantity(quantity)
                .unitId(preparation.getDefaultUnitId())
                .expirationDate(order.expirationDate())
                .saleItemId(order.saleItemId())
                .parentProductionId(order.parentProductionId())
                .notes(order.notes())
                .userId(order.userId())
                .producedAt(order.producedAt() != null ? order.producedAt() : Instant.now())
                .build());

        Consumer consumer = Consumer.forProduction(restaurantId, order.userId(), production);
        BigDecimal totalCost = BigDecimal.ZERO;
        for (RecipeIngredient ingredient : order.recipe().calculateIngredientsForProduction(quantity)) {
            totalCost = totalCost.add(consume(consumer, ingredient.productId(), ingredient.quantity(),
                    ingredient.unitId(), producing));
        }
        BigDecimal costPerUnit = totalCost.divide(quantity, 4, RoundingMode.HALF_UP);

        StockMovement output = stockMovementRepository.save(StockMovement.forProductionOutput(restaurantId,
                preparation.getId(), quantity, preparation.getDefaultUnitId(), costPerUnit,
                stockMovementRepository.getCurrentStock(preparation.getId(), restaurantId), production.getId(),
                order.userId()));
        stockBatchService.createProductionBatch(restaurantId, preparation.getId(), production.getId(),
                output.getQuantity(), order.expirationDate(), costPerUnit, output.getId());

        production.setCostPerUnit(costPerUnit);
        production.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        production = productionRepository.save(production);
        alertEvaluationService.evaluate(preparation.getId(), restaurantId, output.getStockAfter());

        Map<String, Object> payload = new HashMap<>(); // admite null
        payload.put("productId", preparation.getId());
        payload.put("name", preparation.getName());
        payload.put("quantity", quantity);
        payload.put("totalCost", production.getTotalCost());
        payload.put("automatic", production.isAutomatic());
        auditService.log(AuditAction.PRODUCTION_RECORDED, "Production", production.getId(), payload);

        producing.remove(preparation.getId());
        return production;
    }

    /**
     * Los insumos sin ninguna compra no revertida que harían falta para consumir el ítem: el mismo ítem, o si es una
     * preparación, los de su receta (recursivo). Sin eso no hay con qué costear lo que los lotes no cubran.
     */
    public void collectMissingPurchaseCosts(UUID productId, String productName, UUID restaurantId,
                                            List<String> missing, Set<UUID> seen) {
        if (!seen.add(productId)) return;
        Optional<Recipe> preparation = recipeRepository.findByProductIdAndRestaurantId(productId, restaurantId);
        if (preparation.isPresent()) {
            for (RecipeItem item : preparation.get().getItems()) {
                collectMissingPurchaseCosts(item.getProductId(), item.getProductName(), restaurantId, missing, seen);
            }
        } else if (stockMovementRepository.findLastPurchaseCostPerUnit(productId, restaurantId) == null) {
            missing.add(productName);
        }
    }

    /** Ídem para todos los ítems de una receta: agrega a {@code missing} los nombres que falten. */
    public void collectMissingPurchaseCosts(List<RecipeItem> items, UUID restaurantId, List<String> missing) {
        Set<UUID> seen = new HashSet<>();
        for (RecipeItem item : items) {
            collectMissingPurchaseCosts(item.getProductId(), item.getProductName(), restaurantId, missing, seen);
        }
    }

    /** Graba el movimiento y consume los lotes FIFO por su cantidad. Devuelve el movimiento con id. */
    private StockMovement record(StockMovement movement) {
        StockMovement saved = stockMovementRepository.save(movement);
        stockBatchService.consumeFifo(saved.getRestaurantId(), saved.getProductId(), saved.getQuantity(), saved.getId());
        return saved;
    }
}
