package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.PermissionOverrideRepository;
import com.chefcontrol.domain.user.PermissionOverride;
import com.chefcontrol.infrastructure.persistence.entity.PermissionOverrideJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaPermissionOverrideRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PermissionOverrideRepositoryAdapter implements PermissionOverrideRepository {

    private final JpaPermissionOverrideRepository jpa;

    @Override
    public List<PermissionOverride> findByUserIdAndRestaurantId(UUID userId, UUID restaurantId) {
        return jpa.findByUserIdAndRestaurantId(userId, restaurantId).stream()
                .map(PermissionOverrideJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void replaceAll(UUID userId, UUID restaurantId, List<PermissionOverride> overrides) {
        jpa.deleteByUserIdAndRestaurantId(userId, restaurantId);
        jpa.flush();
        for (PermissionOverride override : overrides) {
            jpa.save(PermissionOverrideJpaEntity.from(override));
        }
    }
}
