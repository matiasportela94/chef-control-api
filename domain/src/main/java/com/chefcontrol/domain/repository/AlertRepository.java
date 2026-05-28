package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.alert.Alert;
import com.chefcontrol.domain.alert.AlertType;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository {

    Page<Alert> findByRestaurantIdAndResolvedAtIsNullOrderByCreatedAtDesc(UUID restaurantId, PageRequest pageRequest);

    Optional<Alert> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Optional<Alert> findByProductIdAndTypeAndResolvedAtIsNull(UUID productId, AlertType type);

    Alert save(Alert alert);

    void resolveByProductAndType(UUID productId, AlertType type, Instant now);
}
