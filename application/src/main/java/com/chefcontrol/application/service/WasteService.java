package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.repository.UnitRepository;
import com.chefcontrol.domain.repository.WasteEventRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.MovementDirection;
import com.chefcontrol.domain.stock.MovementSource;
import com.chefcontrol.domain.stock.MovementType;
import com.chefcontrol.domain.stock.StockMovement;
import com.chefcontrol.domain.waste.WasteEvent;
import com.chefcontrol.domain.waste.WasteReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WasteService {

    private final WasteEventRepository wasteEventRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final UnitRepository unitRepository;
    private final AlertEvaluationService alertEvaluationService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public Page<WasteEvent> listWasteEvents(PageRequest pageRequest) {
        return wasteEventRepository.findByRestaurantIdOrderByCreatedAtDesc(TenantContext.require(), pageRequest);
    }

    public WasteEvent getWasteEvent(UUID id) {
        return wasteEventRepository.findByIdAndRestaurantId(id, TenantContext.require())
                .orElseThrow(() -> AppException.notFound(ErrorCode.WASTE_EVENT_NOT_FOUND, "Waste event not found"));
    }

    @Transactional
    public WasteEvent createWasteEvent(CreateWasteEventCommand cmd) {
        UUID restaurantId = TenantContext.require();
        UUID userId = currentUserProvider.currentUserId();

        Product product = productRepository.findByIdAndRestaurantId(cmd.productId(), restaurantId)
                .filter(Product::isActive)
                .orElseThrow(() -> AppException.notFound(ErrorCode.PRODUCT_NOT_FOUND, "Product not found"));

        Unit unit = unitRepository.findById(cmd.unitId())
                .orElseThrow(() -> AppException.notFound(ErrorCode.UNIT_NOT_FOUND, "Unit not found"));

        WasteEvent event = new WasteEvent();
        event.setRestaurantId(restaurantId);
        event.setProductId(product.getId());
        event.setQuantity(cmd.quantity());
        event.setUnitId(unit.getId());
        event.setReason(cmd.reason());
        event.setCost(cmd.cost());
        event.setUserId(userId);
        event = wasteEventRepository.save(event);

        BigDecimal stockBefore = stockMovementRepository.getCurrentStock(product.getId(), restaurantId);

        StockMovement movement = StockMovement.forWaste(
                restaurantId, product.getId(),
                cmd.quantity(), unit.getId(), cmd.cost(),
                stockBefore, event.getId(), userId);
        stockMovementRepository.save(movement);
        alertEvaluationService.evaluate(product.getId(), restaurantId, movement.getStockAfter());

        auditService.log(AuditAction.WASTE_EVENT_CREATED, "WasteEvent", event.getId(),
                Map.of("product", product.getName(), "quantity", cmd.quantity()));

        return event;
    }

    // ── Command ──────────────────────────────────────────────────────────────

    public record CreateWasteEventCommand(
            UUID productId,
            UUID unitId,
            BigDecimal quantity,
            WasteReason reason,
            BigDecimal cost
    ) {}
}
