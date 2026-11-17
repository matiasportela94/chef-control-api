package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.ProductionRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.domain.stock.Production;
import com.chefcontrol.infrastructure.persistence.entity.ProductionJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaProductionRepository;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductionRepositoryAdapter implements ProductionRepository {

    private final JpaProductionRepository jpa;

    @Override
    public Production save(Production production) {
        ProductionJpaEntity saved = jpa.save(ProductionJpaEntity.from(production));
        return jpa.findByIdAndRestaurantId(saved.getId(), saved.getRestaurantId())
                .map(ProductionJpaEntity::toDomain)
                .orElseGet(saved::toDomain);
    }

    @Override
    public Optional<Production> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(ProductionJpaEntity::toDomain);
    }

    @Override
    public List<Production> findBySaleItemIdNewestFirst(UUID saleItemId) {
        return jpa.findBySaleItemIdOrderByCreatedAtDescIdDesc(saleItemId).stream().map(ProductionJpaEntity::toDomain).toList();
    }

    @Override
    public List<Production> findByParentProductionIdNewestFirst(UUID parentProductionId) {
        return jpa.findByParentProductionIdOrderByCreatedAtDescIdDesc(parentProductionId).stream()
                .map(ProductionJpaEntity::toDomain).toList();
    }

    @Override
    public Page<Production> findByRestaurant(UUID restaurantId, UUID productId, Boolean automatic,
                                             Instant from, Instant to, PageRequest pageRequest) {
        // Specification y no "(:x IS NULL OR ...)": con un filtro en null Postgres no infiere el tipo del parámetro.
        Specification<ProductionJpaEntity> spec = (root, query, cb) -> cb.equal(root.get("restaurantId"), restaurantId);
        if (productId != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("productId"), productId));
        if (automatic != null) spec = spec.and((root, query, cb) -> {
            var auto = cb.or(cb.isNotNull(root.get("saleItemId")), cb.isNotNull(root.get("parentProductionId")));
            return automatic ? auto : cb.not(auto);
        });
        if (from != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("producedAt"), from));
        if (to != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("producedAt"), to));
        return PersistenceUtils.toDomain(
                jpa.findAll(spec, PersistenceUtils.toSpring(pageRequest, Sort.by("producedAt").descending()))
                        .map(ProductionJpaEntity::toDomain));
    }
}
