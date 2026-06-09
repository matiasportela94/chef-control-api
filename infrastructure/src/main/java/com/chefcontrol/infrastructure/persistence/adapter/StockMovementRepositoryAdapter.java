package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.StockMovementRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.StockMovement;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import com.chefcontrol.infrastructure.persistence.entity.StockMovementJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaStockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class StockMovementRepositoryAdapter implements StockMovementRepository {

    private final JpaStockMovementRepository jpa;

    @Override
    public BigDecimal getCurrentStock(UUID productId, UUID restaurantId) {
        return jpa.getCurrentStock(productId, restaurantId);
    }

    @Override
    public Page<StockMovement> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByRestaurantIdOrderByCreatedAtDesc(restaurantId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("createdAt").descending()))
                   .map(StockMovementJpaEntity::toDomain));
    }

    @Override
    public Page<StockMovement> findByProductIdAndRestaurantIdOrderByCreatedAtDesc(UUID productId, UUID restaurantId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByProductIdAndRestaurantIdOrderByCreatedAtDesc(productId, restaurantId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("createdAt").descending()))
                   .map(StockMovementJpaEntity::toDomain));
    }

    @Override
    public Optional<StockMovement> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(StockMovementJpaEntity::toDomain);
    }

    @Override
    public List<StockMovement> findByReferenceIdAndReferenceType(UUID referenceId, String referenceType) {
        return jpa.findByReferenceIdAndReferenceType(referenceId, referenceType).stream()
                .map(StockMovementJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public StockMovement save(StockMovement movement) {
        return jpa.save(StockMovementJpaEntity.from(movement)).toDomain();
    }

    @Override
    public void markReversed(UUID id, UUID reversalId) {
        jpa.markReversed(id, reversalId);
    }

    @Override
    public BigDecimal getWeightedAvgPurchaseCost(UUID productId, UUID restaurantId) {
        return jpa.getWeightedAvgPurchaseCost(productId, restaurantId);
    }

    @Override
    public BigDecimal sumSalesCost(UUID restaurantId, Instant from, Instant to) {
        return jpa.sumSalesCost(restaurantId, from, to);
    }

    @Override
    public BigDecimal sumSalesCostByMenuItemAndPeriod(UUID menuItemId, UUID restaurantId, Instant from, Instant to) {
        return jpa.sumSalesCostByMenuItemAndPeriod(menuItemId, restaurantId, from, to);
    }
}
