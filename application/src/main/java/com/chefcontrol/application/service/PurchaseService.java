package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.purchase.PurchaseItem;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.domain.restaurant.TaxCondition;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.valueobject.IvaRateTotal;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.shared.time.ChefControlTime;
import com.chefcontrol.domain.shared.valueobject.IvaRates;
import com.chefcontrol.domain.purchase.PurchaseStatus;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockBatchRepository stockBatchRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final RestaurantRepository restaurantRepository;
    private final StockBatchService stockBatchService;
    private final UnitConversionService unitConversionService;
    private final AlertEvaluationService alertEvaluationService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public Page<Purchase> listPurchases(PageRequest pageRequest) {
        return purchaseRepository.findByRestaurantIdOrderByPurchasedAtDesc(TenantContext.require(), pageRequest);
    }

    public Purchase getPurchase(UUID id) {
        return purchaseRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.PURCHASE_NOT_FOUND, "Purchase not found"));
    }

    public List<PurchaseItem> getPurchaseItems(UUID purchaseId) {
        getPurchase(purchaseId);
        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(purchaseId);
        // El vencimiento no es columna de purchase_items: vive en el lote que esta línea creó.
        // Sale de la misma consulta que quantityRemaining. Sin esto, corregir una compra con
        // reverse+recreate perdía la fecha en silencio y el lote nuevo nacía sin vencimiento.
        items.forEach(item ->
            stockBatchRepository.findByPurchaseItemId(item.getId())
                .ifPresent(batch -> {
                    item.setQuantityRemaining(batch.getQuantityRemaining());
                    item.setExpirationDate(batch.getExpirationDate());
                }));
        return items;
    }

    @Transactional
    public Purchase createPurchase(CreatePurchaseCommand cmd) {
        UUID restaurantId = TenantContext.require();
        UUID userId = currentUserProvider.currentUserId();

        var supplierEntity = cmd.supplierId() != null
                ? supplierRepository.findByIdAndRestaurantId(cmd.supplierId(), restaurantId)
                        .orElseThrow(() -> AppException.notFound(ErrorCode.SUPPLIER_NOT_FOUND, "Supplier not found"))
                : null;

        // Resolve and validate all items before writing anything
        List<ResolvedItem> resolvedItems = cmd.items().stream()
                .map(item -> resolveItem(item, restaurantId))
                .toList();

        Instant purchasedAt = cmd.purchasedAt() != null ? cmd.purchasedAt() : ChefControlTime.nowInstant();
        LocalDate purchaseDate = purchasedAt.atZone(ZoneOffset.UTC).toLocalDate();

        for (ResolvedItem item : resolvedItems) {
            if (item.expirationDate() != null && !item.expirationDate().isAfter(purchaseDate)) {
                throw AppException.badRequest(ErrorCode.INVALID_EXPIRATION_DATE,
                        "Expiration date must be after the purchase date for product " + item.product().getId());
            }
        }

        TaxCondition taxCondition = taxConditionOf(restaurantId);
        boolean pricesIncludeIva = Purchase.pricesIncludeIvaOrDefault(cmd.pricesIncludeIva(), taxCondition);
        List<PurchaseItem> lines = new ArrayList<>();
        for (ResolvedItem item : resolvedItems) {
            PurchaseItem line = PurchaseItem.builder()
                    .productId(item.product().getId())
                    .quantity(item.quantity())
                    .unitId(item.unit().getId())
                    .pricePerUnit(item.pricePerUnit())
                    .build();
            line.applyIva(pricesIncludeIva, item.ivaPercentage());
            lines.add(line);
        }

        Purchase purchase = Purchase.builder()
                .restaurantId(restaurantId)
                .supplierId(supplierEntity != null ? supplierEntity.getId() : null)
                .userId(userId)
                .notes(cmd.notes())
                .purchasedAt(purchasedAt)
                .pricesIncludeIva(pricesIncludeIva)
                .build();
        purchase.totalize(lines);
        purchase = purchaseRepository.save(purchase);

        for (int i = 0; i < lines.size(); i++) {
            ResolvedItem item = resolvedItems.get(i);
            PurchaseItem line = lines.get(i);
            line.setPurchaseId(purchase.getId());
            BigDecimal costPerUnit = line.stockCostPerUnit(pricesIncludeIva, taxCondition);
            PurchaseItem purchaseItem = purchaseItemRepository.save(line);

            BigDecimal stockBefore = stockMovementRepository
                    .getCurrentStock(item.product().getId(), restaurantId);

            StockMovement movement = StockMovement.forPurchase(
                    restaurantId, item.product().getId(),
                    item.quantity(), item.unit().getId(), costPerUnit,
                    stockBefore, purchaseItem.getId(), userId);
            movement = stockMovementRepository.save(movement);

            stockBatchService.createBatch(restaurantId, item.product().getId(), purchaseItem.getId(),
                    item.quantity(), item.expirationDate(), costPerUnit, movement.getId());

            alertEvaluationService.evaluate(item.product().getId(), restaurantId, movement.getStockAfter());
        }

        auditService.log(AuditAction.PURCHASE_CREATED, "Purchase", purchase.getId(),
                Map.of("itemCount", resolvedItems.size(), "total", purchase.getTotal(),
                        "netTotal", purchase.getNetTotal(), "ivaTotal", purchase.getIvaTotal(),
                        "pricesIncludeIva", pricesIncludeIva));
        return purchase;
    }

    private ResolvedItem resolveItem(PurchaseItemCommand item, UUID restaurantId) {
        Product product = productRepository.findByIdAndRestaurantId(item.productId(), restaurantId)
                .filter(Product::isActive)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + item.productId()));
        Unit unit = unitRepository.findById(item.unitId())
                .orElseThrow(() -> AppException.notFound(ErrorCode.UNIT_NOT_FOUND, "Unit not found: " + item.unitId()));
        if (item.ivaPercentage() != null && !IvaRates.isValid(item.ivaPercentage())) {
            throw AppException.badRequest(ErrorCode.INVALID_IVA_PERCENTAGE,
                    "Invalid IVA percentage " + item.ivaPercentage() + " for product " + item.productId());
        }

        // Normalize to the product's default unit so stock and batches are always in one canonical unit
        UUID defaultUnitId = product.getDefaultUnitId();
        BigDecimal quantity     = unitConversionService.convert(item.quantity(), unit.getId(), defaultUnitId);
        BigDecimal pricePerUnit = unitConversionService.convertPrice(item.pricePerUnit(), unit.getId(), defaultUnitId);
        // A la precisión de la columna: el neto y el IVA salen de este precio, y una corrección los vuelve a
        // calcular desde el guardado. Con más decimales acá, recalcular sin cambiar nada movería centavos.
        if (pricePerUnit != null) pricePerUnit = pricePerUnit.setScale(4, RoundingMode.HALF_UP);
        if (pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw AppException.badRequest(ErrorCode.VALIDATION_ERROR,
                    "pricePerUnit must be greater than 0 for product " + item.productId());
        }
        Unit defaultUnit = defaultUnitId.equals(unit.getId()) ? unit
                : unitRepository.findById(defaultUnitId)
                        .orElseThrow(() -> AppException.notFound(ErrorCode.UNIT_NOT_FOUND, "Default unit not found"));

        return new ResolvedItem(product, defaultUnit, quantity, pricePerUnit, item.expirationDate(),
                PurchaseItem.ivaPercentageOrDefault(item.ivaPercentage(), product.getIvaPercentage()));
    }

    /**
     * Corrige precios con la misma regla que el alta: la tasa y si los precios traían IVA son los de la compra, y
     * la condición del restaurante es la de hoy. Una compra anterior al IVA se corrige como siempre (costo = precio).
     */
    @Transactional
    public Purchase updatePurchase(UUID purchaseId, UpdatePurchaseCommand cmd) {
        UUID restaurantId = TenantContext.require();

        Purchase purchase = purchaseRepository.findByIdAndRestaurantId(purchaseId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PURCHASE_NOT_FOUND, "Purchase not found"));

        BigDecimal oldTotal = purchase.getTotal();

        purchase.setSupplierId(cmd.supplierId());
        purchase.setNotes(cmd.notes());
        if (cmd.purchasedAt() != null) purchase.setPurchasedAt(cmd.purchasedAt());

        int itemsUpdated = cmd.items() != null ? cmd.items().size() : 0;
        if (cmd.items() != null) {
            TaxCondition taxCondition = taxConditionOf(restaurantId);
            for (ItemPriceUpdate update : cmd.items()) {
                // De esta compra: el id solo no alcanza, podría ser la línea de otra compra (u otro restaurante).
                PurchaseItem item = purchaseItemRepository.findById(update.id())
                        .filter(i -> purchaseId.equals(i.getPurchaseId()))
                        .orElseThrow(() -> AppException.notFound(ErrorCode.VALIDATION_ERROR, "Item not found: " + update.id()));

                item.setPricePerUnit(update.pricePerUnit().setScale(4, RoundingMode.HALF_UP));
                if (purchase.hasIvaData()) item.applyIva(purchase.getPricesIncludeIva(), item.getIvaPercentage());
                BigDecimal costPerUnit = purchase.hasIvaData()
                        ? item.stockCostPerUnit(purchase.getPricesIncludeIva(), taxCondition)
                        : item.getPricePerUnit();
                purchaseItemRepository.save(item);

                stockMovementRepository.updatePurchaseCostPerUnit(item.getId(), costPerUnit);
                stockBatchRepository.updateCostPerUnitByPurchaseItemId(item.getId(), costPerUnit);
            }
        }

        purchase.totalize(purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(purchaseId));
        BigDecimal newTotal = purchase.getTotal();

        purchase = purchaseRepository.save(purchase);

        auditService.log(AuditAction.PURCHASE_UPDATED, "Purchase", purchase.getId(),
                Map.of("itemsUpdated", itemsUpdated, "oldTotal", oldTotal, "newTotal", newTotal));
        return purchase;
    }

    @Transactional
    public Purchase reversePurchase(UUID purchaseId) {
        UUID restaurantId = TenantContext.require();
        UUID userId = currentUserProvider.currentUserId();

        Purchase purchase = purchaseRepository.findByIdAndRestaurantId(purchaseId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PURCHASE_NOT_FOUND, "Purchase not found"));

        if (purchase.getStatus() == PurchaseStatus.REVERSED) {
            throw AppException.conflict(ErrorCode.VALIDATION_ERROR, "Purchase already reversed");
        }

        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(purchaseId);

        for (PurchaseItem item : items) {
            List<StockMovement> movements = stockMovementRepository
                    .findByReferenceIdAndReferenceType(item.getId(), "purchase_item");

            for (StockMovement original : movements) {
                if (original.getType() == MovementType.REVERSAL || original.getReversedBy() != null) continue;

                BigDecimal stockBefore = stockMovementRepository.getCurrentStock(original.getProductId(), restaurantId);
                StockMovement reversal = StockMovement.builder()
                        .restaurantId(restaurantId)
                        .productId(original.getProductId())
                        .type(MovementType.REVERSAL)
                        .direction(MovementDirection.OUT)
                        .quantity(original.getQuantity())
                        .unitId(original.getUnitId())
                        .costPerUnit(original.getCostPerUnit())
                        .stockBefore(stockBefore)
                        .stockAfter(stockBefore.subtract(original.getQuantity()))
                        .referenceId(original.getId())
                        .referenceType("reversal")
                        .userId(userId)
                        .source(MovementSource.DASHBOARD)
                        .build();
                reversal = stockMovementRepository.save(reversal);
                stockMovementRepository.markReversed(original.getId(), reversal.getId());

                stockBatchRepository.zeroQuantityRemainingByPurchaseItemId(item.getId());

                alertEvaluationService.evaluate(original.getProductId(), restaurantId, reversal.getStockAfter());
            }
        }

        purchase.setStatus(PurchaseStatus.REVERSED);
        purchase = purchaseRepository.save(purchase);

        auditService.log(AuditAction.PURCHASE_REVERSED, "Purchase", purchase.getId(), Map.of());
        return purchase;
    }

    /**
     * IVA de las compras del período, sin las revertidas. Neto, IVA y total salen solo de las compras con datos de
     * IVA; las anteriores al IVA van aparte con su total, porque no se sabe qué parte era IVA.
     */
    public IvaReport getPurchaseIvaReport(Instant from, Instant to) {
        UUID restaurantId = TenantContext.require();
        // ponytail: sin paginar, el período entero en memoria. Alcanza para cientos de compras por mes; si un
        // período llega a miles, agrupar por alícuota en SQL y paginar el listado.
        List<Purchase> purchases = purchaseRepository.findActiveByRestaurantIdAndPurchasedAtBetween(restaurantId, from, to);
        List<Purchase> withIva = purchases.stream().filter(Purchase::hasIvaData).toList();
        List<Purchase> withoutIva = purchases.stream().filter(p -> !p.hasIvaData()).toList();

        Map<BigDecimal, IvaRateTotal> byRate = new TreeMap<>(); // por compareTo: 21 y 21.00 son la misma alícuota
        for (PurchaseItem item : purchaseItemRepository.findByPurchaseIdIn(withIva.stream().map(Purchase::getId).toList())) {
            if (item.getIvaPercentage() == null) continue;
            byRate.merge(item.getIvaPercentage(),
                    new IvaRateTotal(item.getIvaPercentage(), item.getNetAmount(), item.getIvaAmount()),
                    IvaRateTotal::plus);
        }

        BigDecimal netTotal = sum(withIva.stream().map(Purchase::getNetTotal).toList());
        BigDecimal ivaTotal = sum(withIva.stream().map(Purchase::getIvaTotal).toList());
        return new IvaReport(from, to, netTotal, ivaTotal, netTotal.add(ivaTotal), List.copyOf(byRate.values()),
                purchases, withoutIva.size(), sum(withoutIva.stream().map(Purchase::getTotal).toList()));
    }

    private static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private TaxCondition taxConditionOf(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId).map(Restaurant::getTaxCondition).orElse(null);
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /** {@code pricesIncludeIva} null = el default de la condición del restaurante. */
    public record CreatePurchaseCommand(
            UUID supplierId,
            String notes,
            Instant purchasedAt,
            Boolean pricesIncludeIva,
            List<PurchaseItemCommand> items
    ) {}

    /** {@code ivaPercentage} null = la del insumo, y si no tiene, 0. */
    public record PurchaseItemCommand(
            UUID productId,
            UUID unitId,
            BigDecimal quantity,
            BigDecimal pricePerUnit,
            LocalDate expirationDate,
            BigDecimal ivaPercentage
    ) {}

    private record ResolvedItem(Product product, Unit unit, BigDecimal quantity, BigDecimal pricePerUnit,
                                LocalDate expirationDate, BigDecimal ivaPercentage) {}

    public record UpdatePurchaseCommand(
            UUID supplierId,
            String notes,
            Instant purchasedAt,
            List<ItemPriceUpdate> items
    ) {}

    public record ItemPriceUpdate(UUID id, BigDecimal pricePerUnit) {}

    /** {@code total} = neto + IVA de las compras con datos de IVA; las anteriores van en {@code *WithoutIvaData}. */
    public record IvaReport(Instant from, Instant to, BigDecimal netTotal, BigDecimal ivaTotal, BigDecimal total,
                            List<IvaRateTotal> byRate, List<Purchase> purchases,
                            int purchasesWithoutIvaData, BigDecimal totalWithoutIvaData) {}

}
