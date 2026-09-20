package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.RestaurantRepository;
import com.chefcontrol.domain.restaurant.Restaurant;
import com.chefcontrol.infrastructure.persistence.entity.RestaurantJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaRestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RestaurantRepositoryAdapter implements RestaurantRepository {

    private final JpaRestaurantRepository jpa;

    @Override
    public Optional<Restaurant> findByIdAndIsActiveTrue(UUID id) {
        return jpa.findByIdAndIsActiveTrue(id).map(RestaurantJpaEntity::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpa.existsBySlug(slug);
    }

    @Override
    public Restaurant save(Restaurant restaurant) {
        return jpa.save(RestaurantJpaEntity.from(restaurant)).toDomain();
    }

    @Override
    public List<Restaurant> findAllByAccountId(UUID accountId) {
        return jpa.findAllByAccountId(accountId).stream()
                .map(RestaurantJpaEntity::toDomain)
                .collect(Collectors.toList());
    }
}
