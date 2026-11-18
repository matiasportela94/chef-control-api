package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.domain.sale.SaleStatus;
import com.chefcontrol.infrastructure.persistence.entity.SaleJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaSaleRepository extends JpaRepository<SaleJpaEntity, UUID> {

    Page<SaleJpaEntity> findByRestaurantIdOrderBySoldAtDesc(UUID restaurantId, Pageable pageable);

    Optional<SaleJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Optional<SaleJpaEntity> findFirstByRestaurantIdAndReportNumberAndStatus(UUID restaurantId, String reportNumber,
                                                                            com.chefcontrol.domain.sale.SaleStatus status);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM SaleJpaEntity s WHERE s.restaurantId = :restaurantId AND s.status = 'ACTIVE' AND s.soldAt BETWEEN :from AND :to")
    BigDecimal sumTotalAmountByRestaurantIdAndSoldAtBetween(
            @Param("restaurantId") UUID restaurantId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** El ingreso: el neto, o el total si la venta es anterior al IVA (V33). */
    @Query("SELECT COALESCE(SUM(COALESCE(s.netTotal, s.totalAmount)), 0) FROM SaleJpaEntity s WHERE s.restaurantId = :restaurantId AND s.status = 'ACTIVE' AND s.soldAt BETWEEN :from AND :to")
    BigDecimal sumNetRevenueByRestaurantIdAndSoldAtBetween(
            @Param("restaurantId") UUID restaurantId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT s FROM SaleJpaEntity s WHERE s.restaurantId = :restaurantId AND s.status = 'ACTIVE' AND s.soldAt BETWEEN :from AND :to ORDER BY s.soldAt")
    List<SaleJpaEntity> findActiveByRestaurantIdAndSoldAtBetween(
            @Param("restaurantId") UUID restaurantId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    long countByRestaurantIdAndStatusAndSoldAtBetween(UUID restaurantId, SaleStatus status, Instant from, Instant to);
}
