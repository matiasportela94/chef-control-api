package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.CartaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCartaRepository extends JpaRepository<CartaJpaEntity, UUID> {

    List<CartaJpaEntity> findByRestaurantIdOrderByNameAsc(UUID restaurantId);

    Optional<CartaJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);

    /** carta_items es @ElementCollection, no tiene entidad propia: se borra en SQL. */
    @Modifying
    @Query(value = "DELETE FROM carta_items WHERE menu_item_id = :menuItemId", nativeQuery = true)
    void deleteItemsByMenuItemId(@Param("menuItemId") UUID menuItemId);
}
