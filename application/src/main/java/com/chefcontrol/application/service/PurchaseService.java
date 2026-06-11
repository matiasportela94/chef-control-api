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
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.shared.time.ChefControlTime;
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
import java.util.List;
import java.util.Map;
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
        items.forEach(item ->
            stockBatchRepository.findByPurchaseItemId(item.getId())
                .ifPresent(batch -> item.setQuantityRemaining(batch.getQuantityRemaining())));
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

        BigDecimal total = resolvedItems.stream()
                .map(i -> i.pricePerUnit().multiply(i.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Instant purchasedAt = cmd.purchasedAt() != null ? cmd.purchasedAt() : ChefControlTime.nowInstant();
        LocalDate purchaseDate = purchasedAt.atZone(ZoneOffset.UTC).toLocalDate();

        for (ResolvedItem item : resolvedItems) {
            if (item.expirationDate() != null && !item.expirationDate().isAfter(purchaseDate)) {
                throw AppException.badRequest(ErrorCode.INVALID_EXPIRATION_DATE,
                        "Expiration date must be after the purchase date for product " + item.product().getId());
            }
        }

        Purchase purchase = Purchase.builder()
                .restaurantId(restaurantId)
                .supplierId(supplierEntity != null ? supplierEntity.getId() : null)
                .userId(userId)
                .total(total)
                .notes(cmd.notes())
                .purchasedAt(purchasedAt)
                .build();
        purchase = purchaseRepository.save(purchase);

        for (ResolvedItem item : resolvedItems) {
            PurchaseItem purchaseItem = PurchaseItem.builder()
                    .purchaseId(purchase.getId())
                    .productId(item.product().getId())
                    .quantity(item.quantity())
                    .unitId(item.unit().getId())
                    .pricePerUnit(item.pricePerUnit())
                    .build();
            purchaseItem = purchaseItemRepository.save(purchaseItem);

            BigDecimal stockBefore = stockMovementRepository
                    .getCurrentStock(item.product().getId(), restaurantId);

            StockMovement movement = StockMovement.forPurchase(
                    restaurantId, item.product().getId(),
                    item.quantity(), item.unit().getId(), item.pricePerUnit(),
                    stockBefore, purchaseItem.getId(), userId);
            movement = stockMovementRepository.save(movement);

            stockBatchService.createBatch(restaurantId, item.product().getId(), purchaseItem.getId(),
                    item.quantity(), item.expirationDate(), item.pricePerUnit(), movement.getId());

            alertEvaluationService.evaluate(item.product().getId(), restaurantId, movement.getStockAfter());
        }

        auditService.log(AuditAction.PURCHASE_CREATED, "Purchase", purchase.getId(),
                Map.of("itemCount", resolvedItems.size(), "total", total));
        return purchase;
    }

    private ResolvedItem resolveItem(PurchaseItemCommand item, UUID restaurantId) {
        Product product = productRepository.findByIdAndRestaurantId(item.productId(), restaurantId)
                .filter(Product::isActive)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + item.productId()));
        Unit unit = unitRepository.findById(item.unitId())
                .orElseThrow(() -> AppException.notFound(ErrorCode.UNIT_NOT_FOUND, "Unit not found: " + item.unitId()));

        // Normalize to the product's default unit so stock and batches are always in one canonical unit
        UUID defaultUnitId = product.getDefaultUnitId();
        BigDecimal quantity     = unitConversionService.convert(item.quantity(), unit.getId(), defaultUnitId);
        BigDecimal pricePerUnit = unitConversionService.convertPrice(item.pricePerUnit(), unit.getId(), defaultUnitId);
        if (pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw AppException.badRequest(ErrorCode.VALIDATION_ERROR,
                    "pricePerUnit must be greater than 0 for product " + item.productId());
        }
        Unit defaultUnit = defaultUnitId.equals(unit.getId()) ? unit
                : unitRepository.findById(defaultUnitId)
                        .orElseThrow(() -> AppException.notFound(ErrorCode.UNIT_NOT_FOUND, "Default unit not found"));

        return new ResolvedItem(product, defaultUnit, quantity, pricePerUnit, item.expirationDate());
    }

    @Transactional
    public Purchase updatePurchase(UUID purchaseId, UpdatePurchaseCommand cmd) {
        UUID restaurantId = TenantContext.require();

        Purchase purchase = purchaseRepository.findByIdAndRestaurantId(purchaseId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PURCHASE_NOT_FOUND, "Purchase not found"));

        purchase.setSupplierId(cmd.supplierId());
        purchase.setNotes(cmd.notes());
        if (cmd.purchasedAt() != null) purchase.setPurchasedAt(cmd.purchasedAt());

        if (cmd.items() != null) {
            for (ItemPriceUpdate update : cmd.items()) {
                PurchaseItem item = purchaseItemRepository.findById(update.id())
                        .orElseThrow(() -> AppException.notFound(ErrorCode.VALIDATION_ERROR, "Item not found: " + update.id()));

                BigDecimal newPrice = update.pricePerUnit().setScale(4, RoundingMode.HALF_UP);
                item.setPricePerUnit(newPrice);
                purchaseItemRepository.save(item);

                stockMovementRepository.updatePurchaseCostPerUnit(item.getId(), newPrice);
                stockBatchRepository.updateCostPerUnitByPurchaseItemId(item.getId(), newPrice);
            }
        }

        List<PurchaseItem> allItems = purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(purchaseId);
        BigDecimal newTotal = allItems.stream()
                .map(i -> i.getPricePerUnit().multiply(i.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        purchase.setTotal(newTotal);

        return purchaseRepository.save(purchase);
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

        auditService.log(AuditAction.PURCHASE_CREATED, "Purchase", purchase.getId(),
                Map.of("action", "REVERSED"));
        return purchase;
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    public record CreatePurchaseCommand(
            UUID supplierId,
            String notes,
            Instant purchasedAt,
            List<PurchaseItemCommand> items
    ) {}

    public record PurchaseItemCommand(
            UUID productId,
            UUID unitId,
            BigDecimal quantity,
            BigDecimal pricePerUnit,
            LocalDate expirationDate
    ) {}

    private record ResolvedItem(Product product, Unit unit, BigDecimal quantity, BigDecimal pricePerUnit,
                                LocalDate expirationDate) {}

    public record UpdatePurchaseCommand(
            UUID supplierId,
            String notes,
            Instant purchasedAt,
            List<ItemPriceUpdate> items
    ) {}

    public record ItemPriceUpdate(UUID id, BigDecimal pricePerUnit) {}
}
