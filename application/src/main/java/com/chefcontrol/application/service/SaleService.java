package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.sale.SaleItem;
import com.chefcontrol.domain.sale.SaleStatus;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.shared.time.ChefControlTime;
import com.chefcontrol.domain.shared.valueobject.IvaRateTotal;
import com.chefcontrol.domain.stock.MovementDirection;
import com.chefcontrol.domain.stock.MovementSource;
import com.chefcontrol.domain.stock.MovementType;
import com.chefcontrol.domain.stock.StockMovement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final RecipeRepository recipeRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockBatchService stockBatchService;
    private final AlertEvaluationService alertEvaluationService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final ProductRepository productRepository;
    private final UnitConversionService unitConversionService;
    private final RestaurantRepository restaurantRepository;

    public Page<Sale> listSales(PageRequest pageRequest) {
        return saleRepository.findByRestaurantIdOrderBySoldAtDesc(TenantContext.require(), pageRequest);
    }

    public Sale getSale(UUID id) {
        return saleRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.SALE_NOT_FOUND, "Sale not found"));
    }

    public List<SaleItem> getSaleItems(UUID saleId) {
        getSale(saleId); // validates ownership
        return saleItemRepository.findBySaleId(saleId);
    }

    public int getItemCount(UUID saleId) {
        return saleItemRepository.countBySaleId(saleId);
    }

    @Transactional
    public Sale reverseSale(UUID saleId) {
        UUID restaurantId = TenantContext.require();
        UUID userId = currentUserProvider.currentUserId();

        Sale sale = saleRepository.findByIdAndRestaurantId(saleId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.SALE_NOT_FOUND, "Sale not found"));

        if (sale.getStatus() == SaleStatus.REVERSED) {
            throw AppException.conflict(ErrorCode.VALIDATION_ERROR, "Sale already reversed");
        }

        List<SaleItem> items = saleItemRepository.findBySaleId(saleId);

        for (SaleItem item : items) {
            List<StockMovement> movements = stockMovementRepository
                    .findByReferenceIdAndReferenceType(item.getId(), "sale_item");

            for (StockMovement original : movements) {
                if (original.getType() == MovementType.REVERSAL || original.getReversedBy() != null) continue;

                BigDecimal stockBefore = stockMovementRepository.getCurrentStock(original.getProductId(), restaurantId);
                StockMovement reversal = StockMovement.builder()
                        .restaurantId(restaurantId)
                        .productId(original.getProductId())
                        .type(MovementType.REVERSAL)
                        .direction(MovementDirection.IN)
                        .quantity(original.getQuantity())
                        .unitId(original.getUnitId())
                        .costPerUnit(original.getCostPerUnit())
                        .stockBefore(stockBefore)
                        .stockAfter(stockBefore.add(original.getQuantity()))
                        .referenceId(original.getId())
                        .referenceType("reversal")
                        .userId(userId)
                        .source(MovementSource.DASHBOARD)
                        .build();
                reversal = stockMovementRepository.save(reversal);
                stockMovementRepository.markReversed(original.getId(), reversal.getId());
                stockBatchService.reverseAllocations(restaurantId, original.getId(), reversal.getId());

                alertEvaluationService.evaluate(original.getProductId(), restaurantId, reversal.getStockAfter());
            }
        }

        sale.setStatus(SaleStatus.REVERSED);
        sale = saleRepository.save(sale);

        auditService.log(AuditAction.SALE_REVERSED, "Sale", sale.getId(), Map.of());
        return sale;
    }

    @Transactional
    public Sale recordSale(CreateSaleCommand cmd) {
        UUID restaurantId = TenantContext.require();
        UUID userId = currentUserProvider.currentUserId();

        List<ResolvedItem> resolvedItems = cmd.items().stream()
                .map(item -> resolveItem(item, restaurantId))
                .toList();

        validateIngredientCosts(resolvedItems, restaurantId);

        List<SaleItem> lines = resolvedItems.stream()
                .map(r -> SaleItem.builder()
                        .menuItemId(r.menuItem().getId())
                        .quantity(r.cmd().quantity())
                        .unitPrice(r.menuItem().getPrice())
                        .build())
                .toList();

        Instant soldAt = cmd.soldAt() != null ? cmd.soldAt() : ChefControlTime.nowInstant();

        Sale sale = Sale.builder()
                .restaurantId(restaurantId)
                .userId(userId)
                .source("manual")
                .notes(cmd.notes())
                .soldAt(soldAt)
                .build();
        // La alícuota se congela acá, con la condición de hoy: la venta no se recalcula si el restaurante cambia.
        sale.totalize(restaurantRepository.findById(restaurantId).map(Restaurant::salesIvaRate).orElse(BigDecimal.ZERO),
                lines);
        sale = saleRepository.save(sale);

        for (int i = 0; i < lines.size(); i++) {
            SaleItem saleItem = saleItemRepository.save(lines.get(i).toBuilder().saleId(sale.getId()).build());
            applyRecipeStockMovements(resolvedItems.get(i).menuItem(), resolvedItems.get(i).cmd().quantity(),
                    restaurantId, userId, saleItem.getId());
        }

        auditService.log(AuditAction.SALE_RECORDED, "Sale", sale.getId(),
                Map.of("itemCount", resolvedItems.size(), "total", sale.getTotalAmount(),
                        "netTotal", sale.getNetTotal(), "ivaPercentage", sale.getIvaPercentage()));
        return sale;
    }

    /**
     * El IVA de las ventas del período, sin las revertidas: lo que el RI le debe al fisco (débito fiscal). Mismo
     * criterio que el de compras: {@code netTotal + ivaTotal = total} cuentan solo las ventas con datos de IVA; las
     * anteriores suman aparte.
     */
    public SalesIvaReport getSalesIvaReport(Instant from, Instant to) {
        UUID restaurantId = TenantContext.require();
        // ponytail: sin paginar, el período entero en memoria, como el de compras. Si un período llega a miles de
        // ventas, agrupar por alícuota en SQL y paginar el listado.
        List<Sale> sales = saleRepository.findActiveByRestaurantIdAndSoldAtBetween(restaurantId, from, to);
        List<Sale> withIva = sales.stream().filter(Sale::hasIvaData).toList();
        List<Sale> withoutIva = sales.stream().filter(s -> !s.hasIvaData()).toList();

        Map<BigDecimal, IvaRateTotal> byRate = new TreeMap<>(); // por compareTo: 21 y 21.00 son la misma alícuota
        withIva.forEach(s -> byRate.merge(s.getIvaPercentage(),
                new IvaRateTotal(s.getIvaPercentage(), s.getNetTotal(), s.getIvaTotal()), IvaRateTotal::plus));

        BigDecimal netTotal = sum(withIva.stream().map(Sale::getNetTotal));
        BigDecimal ivaTotal = sum(withIva.stream().map(Sale::getIvaTotal));
        return new SalesIvaReport(from, to, netTotal, ivaTotal, netTotal.add(ivaTotal), List.copyOf(byRate.values()),
                sales, withoutIva.size(), sum(withoutIva.stream().map(Sale::getTotalAmount)));
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> amounts) {
        return amounts.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void applyRecipeStockMovements(MenuItem menuItem, int quantity,
                                           UUID restaurantId, UUID userId, UUID saleItemId) {
        recipeRepository.findByMenuItemIdAndRestaurantId(menuItem.getId(), restaurantId)
                .ifPresent(recipe -> {
                    for (var ingredient : recipe.calculateIngredientsForQuantity(quantity)) {
                        Product product = productRepository
                                .findByIdAndRestaurantId(ingredient.productId(), restaurantId)
                                .orElse(null);
                        UUID defaultUnitId = product != null ? product.getDefaultUnitId() : ingredient.unitId();

                        // Se redondea el neto a la precisión con la que se va a guardar ANTES de
                        // restar, para que SALE + WASTE dé exactamente el bruto y no quede un
                        // resto de medio gramo colgado en ningún lado.
                        BigDecimal netQuantity = unitConversionService.convert(
                                ingredient.quantity(), ingredient.unitId(), defaultUnitId)
                                .setScale(3, RoundingMode.HALF_UP);
                        BigDecimal grossQuantity = (product != null
                                ? product.grossQuantityFor(netQuantity)
                                : netQuantity).setScale(3, RoundingMode.HALF_UP);

                        BigDecimal avgCost = stockMovementRepository
                                .getWeightedAvgPurchaseCost(ingredient.productId(), restaurantId);

                        // El stock se descuenta por el bruto, pero partido en dos movimientos: lo que
                        // llega al plato como SALE y la merma de limpieza como WASTE, para que quede
                        // medible en vez de escondida dentro del consumo.
                        // Con rendimiento > 100 (el arroz absorbe agua) el bruto es MENOR que el neto:
                        // no hay merma, y el SALE va por el bruto.
                        BigDecimal saleQuantity = netQuantity.min(grossQuantity);
                        BigDecimal yieldWaste = grossQuantity.subtract(netQuantity);

                        StockMovement movement = recordConsumption(StockMovement.forSale(
                                restaurantId, ingredient.productId(), saleQuantity, defaultUnitId, avgCost,
                                stockMovementRepository.getCurrentStock(ingredient.productId(), restaurantId),
                                saleItemId, userId), ingredient.productId(), restaurantId);

                        if (yieldWaste.compareTo(BigDecimal.ZERO) > 0) {
                            movement = recordConsumption(StockMovement.forStandardYieldWaste(
                                    restaurantId, ingredient.productId(), yieldWaste, defaultUnitId, avgCost,
                                    stockMovementRepository.getCurrentStock(ingredient.productId(), restaurantId),
                                    saleItemId, userId), ingredient.productId(), restaurantId);
                        }

                        alertEvaluationService.evaluate(ingredient.productId(), restaurantId, movement.getStockAfter());
                    }
                });
    }

    /** Graba el movimiento y consume los lotes FIFO por su cantidad. Devuelve el movimiento con id. */
    private StockMovement recordConsumption(StockMovement movement, UUID productId, UUID restaurantId) {
        StockMovement saved = stockMovementRepository.save(movement);
        stockBatchService.consumeFifo(restaurantId, productId, saved.getQuantity(), saved.getId());
        return saved;
    }

    private void validateIngredientCosts(List<ResolvedItem> resolvedItems, UUID restaurantId) {
        List<String> missing = new ArrayList<>();
        for (ResolvedItem resolved : resolvedItems) {
            recipeRepository.findByMenuItemIdAndRestaurantId(resolved.menuItem().getId(), restaurantId)
                    .ifPresent(recipe -> recipe.getItems().stream()
                            .filter(item -> stockMovementRepository
                                    .getWeightedAvgPurchaseCost(item.getProductId(), restaurantId)
                                    .compareTo(BigDecimal.ZERO) == 0)
                            .map(item -> item.getProductName())
                            .forEach(missing::add));
        }
        if (!missing.isEmpty()) {
            throw AppException.badRequest(ErrorCode.MISSING_PURCHASE_COST,
                    "Sin historial de compras para: " + String.join(", ", missing));
        }
    }

    private ResolvedItem resolveItem(SaleItemCommand cmd, UUID restaurantId) {
        MenuItem menuItem = menuItemRepository.findByIdAndRestaurantId(cmd.menuItemId(), restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.MENU_ITEM_NOT_FOUND,
                        "Menu item not found: " + cmd.menuItemId()));
        menuItem.validateCanBeSold();
        return new ResolvedItem(menuItem, cmd);
    }

    // ── Commands / Results ────────────────────────────────────────────────────

    public record CreateSaleCommand(String notes, Instant soldAt, List<SaleItemCommand> items) {}

    public record SaleItemCommand(UUID menuItemId, int quantity) {}

    private record ResolvedItem(MenuItem menuItem, SaleItemCommand cmd) {}

    public record SalesIvaReport(Instant from, Instant to, BigDecimal netTotal, BigDecimal ivaTotal, BigDecimal total,
                                 List<IvaRateTotal> byRate, List<Sale> sales,
                                 int salesWithoutIvaData, BigDecimal totalWithoutIvaData) {}
}
