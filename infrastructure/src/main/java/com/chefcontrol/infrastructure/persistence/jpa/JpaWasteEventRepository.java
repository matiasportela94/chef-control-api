package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.WasteEventJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface JpaWasteEventRepository extends JpaRepository<WasteEventJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"product", "unit"})
    Page<WasteEventJpaEntity> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "unit"})
    Optional<WasteEventJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    /**
     * Sin las deshechas. Una merma no tiene estado propio: deshacerla revierte su movimiento WASTE y el waste_event queda,
     * así que lo que la marca es {@code reversed_by}. EXISTS y no un JOIN: un evento con más de un movimiento cuenta una
     * vez.
     */
    String NOT_UNDONE = " AND EXISTS (SELECT 1 FROM stock_movements m WHERE m.reference_id = w.id"
            + " AND m.reference_type = 'waste_event' AND m.type = 'WASTE' AND m.reversed_by IS NULL)";

    @Query(value = "SELECT COUNT(*) FROM waste_events w WHERE w.restaurant_id = :restaurantId AND w.created_at >= :since"
            + NOT_UNDONE, nativeQuery = true)
    long countNotUndoneSince(@Param("restaurantId") UUID restaurantId, @Param("since") Instant since);

    @Query(value = "SELECT COUNT(*) FROM waste_events w WHERE w.restaurant_id = :restaurantId"
            + " AND w.created_at BETWEEN :from AND :to" + NOT_UNDONE, nativeQuery = true)
    long countNotUndoneBetween(@Param("restaurantId") UUID restaurantId, @Param("from") Instant from,
                               @Param("to") Instant to);

    @Query(value = "SELECT COALESCE(SUM(w.cost), 0) FROM waste_events w WHERE w.restaurant_id = :restaurantId"
            + " AND w.created_at BETWEEN :from AND :to" + NOT_UNDONE, nativeQuery = true)
    BigDecimal sumCostNotUndoneBetween(@Param("restaurantId") UUID restaurantId, @Param("from") Instant from,
                                       @Param("to") Instant to);
}
