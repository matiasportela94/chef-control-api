package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockCountService {

    private final StockCountRepository stockCountRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final AlertEvaluationService alertEvaluationService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public Page<StockCount> listCounts(PageRequest pageRequest) {
        return stockCountRepository.findByRestaurantIdOrderByCountedAtDesc(TenantContext.require(), pageRequest);
    }

    public StockCountResult getCount(UUID id) {
        UUID restaurantId = TenantContext.require();
        StockCount count = stockCountRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.STOCK_COUNT_NOT_FOUND, "Stock count not found"));
        List<StockMovement> movements = stockMovementRepository
                .findByReferenceIdAndReferenceType(count.getId(), "stock_count");
        return new StockCountResult(count, movements);
    }

    @Transactional
    public StockCountResult createStockCount(CreateStockCountCommand cmd) {
        UUID restaurantId = TenantContext.require();
        UUID userId = currentUserProvider.currentUserId();

        StockCount count = new StockCount();
        count.setRestaurantId(restaurantId);
        count.setUserId(userId);
        count.setNotes(cmd.notes());
        count = stockCountRepository.save(count);

        List<StockMovement> movements = new ArrayList<>();

        for (StockCountItemCommand item : cmd.items()) {
            Product product = productRepository.findByIdAndRestaurantId(item.productId(), restaurantId)
                    .filter(Product::isActive)
                    .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND,
                            "Product not found: " + item.productId()));

            var unit = unitRepository.findById(item.unitId())
                    .orElseThrow(() -> AppException.notFound(ErrorCode.UNIT_NOT_FOUND,
                            "Unit not found: " + item.unitId()));

            BigDecimal currentStock = stockMovementRepository.getCurrentStock(product.getId(), restaurantId);

            if (item.countedQuantity().compareTo(currentStock) == 0) continue;

            StockMovement movement = StockMovement.forAdjustment(
                    restaurantId, product.getId(),
                    currentStock, item.countedQuantity(),
                    unit.getId(), count.getId(), userId);
            movements.add(stockMovementRepository.save(movement));
            alertEvaluationService.evaluate(product.getId(), restaurantId, movement.getStockAfter());
        }

        auditService.log(AuditAction.STOCK_COUNT_CREATED, "StockCount", count.getId(),
                Map.of("adjustments", movements.size(), "itemsChecked", cmd.items().size()));

        return new StockCountResult(count, movements);
    }

    // ── Commands / Results ────────────────────────────────────────────────────

    public record CreateStockCountCommand(String notes, List<StockCountItemCommand> items) {}

    public record StockCountItemCommand(UUID productId, UUID unitId, BigDecimal countedQuantity) {}

    public record StockCountResult(StockCount count, List<StockMovement> movements) {}
}
