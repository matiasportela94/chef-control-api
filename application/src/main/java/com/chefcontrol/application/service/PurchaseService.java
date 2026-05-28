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
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.shared.time.ChefControlTime;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
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
        // Implicitly validates purchase belongs to tenant via getPurchase
        getPurchase(purchaseId);
        return purchaseItemRepository.findByPurchaseIdOrderByCreatedAtAsc(purchaseId);
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
            stockMovementRepository.save(movement);
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
        return new ResolvedItem(product, unit, item.quantity(), item.pricePerUnit());
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
            BigDecimal pricePerUnit
    ) {}

    private record ResolvedItem(Product product, Unit unit, BigDecimal quantity, BigDecimal pricePerUnit) {}
}
