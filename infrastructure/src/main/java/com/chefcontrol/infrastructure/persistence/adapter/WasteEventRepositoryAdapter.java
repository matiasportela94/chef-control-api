package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.WasteEventRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.waste.WasteEvent;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import com.chefcontrol.infrastructure.persistence.entity.WasteEventJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaWasteEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WasteEventRepositoryAdapter implements WasteEventRepository {

    private final JpaWasteEventRepository jpa;

    @Override
    public Page<WasteEvent> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByRestaurantIdOrderByCreatedAtDesc(restaurantId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("createdAt").descending()))
                   .map(WasteEventJpaEntity::toDomain));
    }

    @Override
    public Optional<WasteEvent> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(WasteEventJpaEntity::toDomain);
    }

    @Override
    public WasteEvent save(WasteEvent event) {
        WasteEventJpaEntity saved = jpa.save(WasteEventJpaEntity.from(event));
        return jpa.findByIdAndRestaurantId(saved.getId(), saved.getRestaurantId())
                .map(WasteEventJpaEntity::toDomain)
                .orElse(saved.toDomain());
    }

    @Override
    public long countByRestaurantIdAndCreatedAtGreaterThanEqual(UUID restaurantId, Instant since) {
        return jpa.countByRestaurantIdAndCreatedAtGreaterThanEqual(restaurantId, since);
    }

    @Override
    public BigDecimal sumCostByRestaurantIdAndCreatedAtBetween(UUID restaurantId, Instant from, Instant to) {
        return jpa.sumCostByRestaurantIdAndCreatedAtBetween(restaurantId, from, to);
    }
}
