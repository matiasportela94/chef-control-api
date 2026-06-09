package com.chefcontrol.application.service;

import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.MovementDirection;
import com.chefcontrol.domain.stock.MovementSource;
import com.chefcontrol.domain.stock.MovementType;
import com.chefcontrol.domain.stock.StockMovement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMovementRepository stockMovementRepository;
    private final StockBatchService stockBatchService;
    private final AlertEvaluationService alertEvaluationService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    public BigDecimal getCurrentStock(UUID productId) {
        return stockMovementRepository.getCurrentStock(productId, TenantContext.require());
    }

    public BigDecimal getCurrentStock(UUID productId, UUID restaurantId) {
        return stockMovementRepository.getCurrentStock(productId, restaurantId);
    }

    public Page<StockMovement> listMovements(PageRequest pageRequest) {
        return stockMovementRepository.findByRestaurantIdOrderByCreatedAtDesc(TenantContext.require(), pageRequest);
    }

    public Page<StockMovement> listMovementsByProduct(UUID productId, PageRequest pageRequest) {
        return stockMovementRepository.findByProductIdAndRestaurantIdOrderByCreatedAtDesc(
                productId, TenantContext.require(), pageRequest);
    }

    @Transactional
    public StockMovement reverseMovement(UUID movementId) {
        UUID restaurantId = TenantContext.require();
        UUID userId = currentUserProvider.currentUserId();

        StockMovement original = stockMovementRepository.findByIdAndRestaurantId(movementId, restaurantId)
                .orElseThrow(() -> AppException.notFound(ErrorCode.MOVEMENT_NOT_FOUND, "Stock movement not found"));

        if (original.getReversedBy() != null) {
            throw AppException.conflict(ErrorCode.MOVEMENT_ALREADY_REVERSED, "Movement already reversed");
        }
        if (original.getType() == MovementType.REVERSAL) {
            throw AppException.badRequest(ErrorCode.MOVEMENT_CANNOT_BE_REVERSED, "Cannot reverse a reversal movement");
        }

        MovementDirection reversalDir = original.getDirection() == MovementDirection.IN
                ? MovementDirection.OUT : MovementDirection.IN;

        BigDecimal stockBefore = stockMovementRepository.getCurrentStock(original.getProductId(), restaurantId);
        BigDecimal stockAfter = reversalDir == MovementDirection.IN
                ? stockBefore.add(original.getQuantity())
                : stockBefore.subtract(original.getQuantity());

        StockMovement reversal = StockMovement.builder()
                .restaurantId(restaurantId)
                .productId(original.getProductId())
                .type(MovementType.REVERSAL)
                .direction(reversalDir)
                .quantity(original.getQuantity())
                .unitId(original.getUnitId())
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .referenceId(original.getId())
                .referenceType("reversal")
                .userId(userId)
                .source(MovementSource.DASHBOARD)
                .build();
        reversal = stockMovementRepository.save(reversal);

        stockMovementRepository.markReversed(original.getId(), reversal.getId());

        stockBatchService.reverseAllocations(restaurantId, original.getId(), reversal.getId());

        alertEvaluationService.evaluate(original.getProductId(), restaurantId, stockAfter);

        auditService.log(AuditAction.STOCK_MOVEMENT_REVERSED, "StockMovement", original.getId(),
                Map.of("reversalId", reversal.getId()));

        return reversal;
    }
}
