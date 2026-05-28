package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.UserRestaurantRepository;
import com.chefcontrol.domain.user.UserRestaurant;
import com.chefcontrol.infrastructure.persistence.entity.RestaurantJpaEntity;
import com.chefcontrol.infrastructure.persistence.entity.RoleJpaEntity;
import com.chefcontrol.infrastructure.persistence.entity.UserJpaEntity;
import com.chefcontrol.infrastructure.persistence.entity.UserRestaurantJpaEntity;
import com.chefcontrol.infrastructure.persistence.entity.UserRestaurantJpaId;
import com.chefcontrol.infrastructure.persistence.jpa.JpaUserRestaurantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UserRestaurantRepositoryAdapter implements UserRestaurantRepository {

    private final JpaUserRestaurantRepository jpa;
    private final EntityManager em;

    @Override
    public List<UserRestaurant> findActiveByUserId(UUID userId) {
        return jpa.findActiveByUserId(userId).stream()
                .map(UserRestaurantJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<UserRestaurant> findActiveByUserIdAndRestaurantId(UUID userId, UUID restaurantId) {
        return jpa.findActiveByUserIdAndRestaurantId(userId, restaurantId)
                .map(UserRestaurantJpaEntity::toDomain);
    }

    @Override
    public List<UserRestaurant> findActiveByRestaurantId(UUID restaurantId) {
        return jpa.findActiveByRestaurantId(restaurantId).stream()
                .map(UserRestaurantJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<UserRestaurant> findByUserIdAndRestaurantId(UUID userId, UUID restaurantId) {
        return jpa.findByUserIdAndRestaurantId(userId, restaurantId)
                .map(UserRestaurantJpaEntity::toDomain);
    }

    @Override
    public UserRestaurant save(UserRestaurant domain) {
        UserRestaurantJpaEntity entity = new UserRestaurantJpaEntity();
        entity.setId(new UserRestaurantJpaId(domain.getUserId(), domain.getRestaurantId()));
        entity.setUser(em.getReference(UserJpaEntity.class, domain.getUserId()));
        entity.setRestaurant(em.getReference(RestaurantJpaEntity.class, domain.getRestaurantId()));
        entity.setRole(em.getReference(RoleJpaEntity.class, domain.getRoleId()));
        entity.setActive(domain.isActive());
        entity.setCreatedAt(domain.getCreatedAt());
        return jpa.save(entity).toDomain();
    }
}
