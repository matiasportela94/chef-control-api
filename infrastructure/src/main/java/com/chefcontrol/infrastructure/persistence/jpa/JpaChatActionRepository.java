package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.ChatActionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaChatActionRepository extends JpaRepository<ChatActionJpaEntity, UUID> {

    Optional<ChatActionJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    java.util.List<ChatActionJpaEntity> findAllByIdInAndRestaurantId(java.util.Collection<UUID> ids, UUID restaurantId);
}
