package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.purchase.Purchase;
import com.chefcontrol.domain.purchase.PurchaseStatus;
import com.chefcontrol.domain.repository.PurchaseRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import com.chefcontrol.infrastructure.persistence.entity.PurchaseJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PurchaseRepositoryAdapter implements PurchaseRepository {

    private final JpaPurchaseRepository jpa;

    @Override
    public Page<Purchase> findByRestaurantIdOrderByPurchasedAtDesc(UUID restaurantId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByRestaurantIdOrderByPurchasedAtDesc(restaurantId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("purchasedAt").descending()))
                   .map(PurchaseJpaEntity::toDomain));
    }

    @Override
    public Optional<Purchase> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(PurchaseJpaEntity::toDomain);
    }

    @Override
    public Purchase save(Purchase purchase) {
        PurchaseJpaEntity saved = jpa.save(PurchaseJpaEntity.from(purchase));
        return jpa.findByIdAndRestaurantId(saved.getId(), saved.getRestaurantId())
                .map(PurchaseJpaEntity::toDomain)
                .orElse(saved.toDomain());
    }

    @Override
    public long countByRestaurantIdAndPurchasedAtGreaterThanEqual(UUID restaurantId, Instant since) {
        return jpa.countByRestaurantIdAndStatusAndPurchasedAtGreaterThanEqual(restaurantId, PurchaseStatus.ACTIVE, since);
    }

    @Override
    public BigDecimal sumTotalByRestaurantIdAndPurchasedAtSince(UUID restaurantId, Instant since) {
        return jpa.sumTotalByRestaurantIdAndPurchasedAtSince(restaurantId, since);
    }
}
