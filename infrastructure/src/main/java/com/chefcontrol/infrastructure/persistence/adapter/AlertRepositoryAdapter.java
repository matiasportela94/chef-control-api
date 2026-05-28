package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.alert.Alert;
import com.chefcontrol.domain.alert.AlertType;
import com.chefcontrol.domain.repository.AlertRepository;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import com.chefcontrol.infrastructure.persistence.entity.AlertJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AlertRepositoryAdapter implements AlertRepository {

    private final JpaAlertRepository jpa;

    @Override
    public Page<Alert> findByRestaurantIdAndResolvedAtIsNullOrderByCreatedAtDesc(UUID restaurantId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByRestaurantIdAndResolvedAtIsNullOrderByCreatedAtDesc(restaurantId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("createdAt").descending()))
                   .map(AlertJpaEntity::toDomain));
    }

    @Override
    public Optional<Alert> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(AlertJpaEntity::toDomain);
    }

    @Override
    public Optional<Alert> findByProductIdAndTypeAndResolvedAtIsNull(UUID productId, AlertType type) {
        return jpa.findByProductIdAndTypeAndResolvedAtIsNull(productId, type).map(AlertJpaEntity::toDomain);
    }

    @Override
    public Alert save(Alert alert) {
        return jpa.save(AlertJpaEntity.from(alert)).toDomain();
    }

    @Override
    public void resolveByProductAndType(UUID productId, AlertType type, Instant now) {
        jpa.resolveByProductAndType(productId, type, now);
    }
}
