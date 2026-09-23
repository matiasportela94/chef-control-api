package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.menu.Carta;
import com.chefcontrol.domain.repository.CartaRepository;
import com.chefcontrol.infrastructure.persistence.entity.CartaJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaCartaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CartaRepositoryAdapter implements CartaRepository {

    private final JpaCartaRepository jpa;

    @Override
    public List<Carta> findAllByRestaurantId(UUID restaurantId) {
        return jpa.findByRestaurantIdOrderByNameAsc(restaurantId).stream()
                .map(CartaJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Carta> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(CartaJpaEntity::toDomain);
    }

    @Override
    public boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name) {
        return jpa.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name);
    }

    @Override
    public Carta save(Carta carta) {
        return jpa.save(CartaJpaEntity.from(carta)).toDomain();
    }

    @Override
    public void removeMenuItemFromAllCartas(UUID menuItemId) {
        jpa.deleteItemsByMenuItemId(menuItemId);
    }

    @Override
    public void delete(UUID id) {
        jpa.deleteById(id);
    }
}
