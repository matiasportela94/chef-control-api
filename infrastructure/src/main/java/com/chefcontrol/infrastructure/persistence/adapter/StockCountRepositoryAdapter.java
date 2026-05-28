package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.StockCountRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.StockCount;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import com.chefcontrol.infrastructure.persistence.entity.StockCountJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaStockCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StockCountRepositoryAdapter implements StockCountRepository {

    private final JpaStockCountRepository jpa;

    @Override
    public Page<StockCount> findByRestaurantIdOrderByCountedAtDesc(UUID restaurantId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByRestaurantIdOrderByCountedAtDesc(restaurantId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("countedAt").descending()))
                   .map(StockCountJpaEntity::toDomain));
    }

    @Override
    public Optional<StockCount> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(StockCountJpaEntity::toDomain);
    }

    @Override
    public StockCount save(StockCount count) {
        return jpa.save(StockCountJpaEntity.from(count)).toDomain();
    }
}
