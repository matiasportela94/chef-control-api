package com.chefcontrol.application.service;

import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.AuditService;
import com.chefcontrol.application.port.CurrentUserProvider;
import com.chefcontrol.domain.audit.AuditAction;
import com.chefcontrol.domain.context.TenantContext;
import com.chefcontrol.domain.menu.MenuItem;
import com.chefcontrol.domain.menu.RecipeItem;
import com.chefcontrol.domain.repository.*;
import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.sale.SaleItem;
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
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final RecipeRepository recipeRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AlertEvaluationService alertEvaluationService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

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

    @Transactional
    public Sale recordSale(CreateSaleCommand cmd) {
        UUID restaurantId = TenantContext.require();
        UUID userId = currentUserProvider.currentUserId();

        List<ResolvedItem> resolvedItems = cmd.items().stream()
                .map(item -> resolveItem(item, restaurantId))
                .toList();

        BigDecimal totalAmount = resolvedItems.stream()
                .map(i -> i.menuItem().getPrice()
                        .multiply(BigDecimal.valueOf(i.cmd().quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Instant soldAt = cmd.soldAt() != null ? cmd.soldAt() : ChefControlTime.nowInstant();

        Sale sale = Sale.builder()
                .restaurantId(restaurantId)
                .userId(userId)
                .totalAmount(totalAmount)
                .source("manual")
                .notes(cmd.notes())
                .soldAt(soldAt)
                .build();
        sale = saleRepository.save(sale);

        for (ResolvedItem resolved : resolvedItems) {
            SaleItem saleItem = SaleItem.builder()
                    .saleId(sale.getId())
                    .menuItemId(resolved.menuItem().getId())
                    .quantity(resolved.cmd().quantity())
                    .unitPrice(resolved.menuItem().getPrice())
                    .build();
            saleItemRepository.save(saleItem);

            applyRecipeStockMovements(resolved.menuItem(), resolved.cmd().quantity(),
                    restaurantId, userId, saleItem.getId());
        }

        auditService.log(AuditAction.SALE_RECORDED, "Sale", sale.getId(),
                Map.of("itemCount", resolvedItems.size(), "total", totalAmount));
        return sale;
    }

    private void applyRecipeStockMovements(MenuItem menuItem, int quantity,
                                           UUID restaurantId, UUID userId, UUID saleItemId) {
        recipeRepository.findByMenuItemIdAndRestaurantId(menuItem.getId(), restaurantId)
                .ifPresent(recipe -> {
                    for (var ingredient : recipe.calculateIngredientsForQuantity(quantity)) {
                        BigDecimal avgCost = stockMovementRepository
                                .getWeightedAvgPurchaseCost(ingredient.productId(), restaurantId);
                        BigDecimal stockBefore = stockMovementRepository
                                .getCurrentStock(ingredient.productId(), restaurantId);

                        StockMovement movement = StockMovement.forSale(
                                restaurantId, ingredient.productId(),
                                ingredient.quantity(), ingredient.unitId(), avgCost,
                                stockBefore, saleItemId, userId);
                        stockMovementRepository.save(movement);
                        alertEvaluationService.evaluate(ingredient.productId(), restaurantId, movement.getStockAfter());
                    }
                });
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
}
