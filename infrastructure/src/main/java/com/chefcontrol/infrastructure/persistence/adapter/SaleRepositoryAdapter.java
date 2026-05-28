package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.SaleRepository;
import com.chefcontrol.domain.sale.Sale;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import com.chefcontrol.infrastructure.persistence.entity.SaleJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaSaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SaleRepositoryAdapter implements SaleRepository {

    private final JpaSaleRepository jpa;

    @Override
    public Page<Sale> findByRestaurantIdOrderBySoldAtDesc(UUID restaurantId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByRestaurantIdOrderBySoldAtDesc(restaurantId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("soldAt").descending()))
                   .map(SaleJpaEntity::toDomain));
    }

    @Override
    public Optional<Sale> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(SaleJpaEntity::toDomain);
    }

    @Override
    public Sale save(Sale sale) {
        return jpa.save(SaleJpaEntity.from(sale)).toDomain();
    }

    @Override
    public BigDecimal sumTotalAmountByRestaurantIdAndSoldAtBetween(UUID restaurantId, Instant from, Instant to) {
        return jpa.sumTotalAmountByRestaurantIdAndSoldAtBetween(restaurantId, from, to);
    }
}
