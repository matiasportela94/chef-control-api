package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.StockBatchRepository;
import com.chefcontrol.domain.stock.StockBatch;
import com.chefcontrol.infrastructure.persistence.entity.StockBatchJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaStockBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class StockBatchRepositoryAdapter implements StockBatchRepository {

    private final JpaStockBatchRepository jpa;

    @Override
    public StockBatch save(StockBatch batch) {
        return jpa.save(StockBatchJpaEntity.from(batch)).toDomain();
    }

    @Override
    public Optional<StockBatch> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(StockBatchJpaEntity::toDomain);
    }

    @Override
    public List<StockBatch> findAvailableByProductFifo(UUID productId, UUID restaurantId) {
        return jpa.findAvailableByProductFifo(productId, restaurantId).stream()
                .map(StockBatchJpaEntity::toDomain)
                .collect(Collectors.toList());
    }
}
