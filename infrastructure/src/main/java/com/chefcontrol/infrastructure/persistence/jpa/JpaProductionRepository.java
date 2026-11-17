package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.ProductionJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaProductionRepository extends JpaRepository<ProductionJpaEntity, UUID>,
        JpaSpecificationExecutor<ProductionJpaEntity> {

    // El listado: con el nombre del insumo, de la unidad y la venta en la misma consulta.
    @Override
    @EntityGraph(attributePaths = {"product", "unit", "saleItem"})
    org.springframework.data.domain.Page<ProductionJpaEntity> findAll(
            org.springframework.data.jpa.domain.Specification<ProductionJpaEntity> spec,
            org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"product", "unit", "saleItem"})
    Optional<ProductionJpaEntity> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    // Por created_at y después id: dos producciones de la misma venta pueden caer en el mismo instante.
    @EntityGraph(attributePaths = {"product", "unit", "saleItem"})
    List<ProductionJpaEntity> findBySaleItemIdOrderByCreatedAtDescIdDesc(UUID saleItemId);

    @EntityGraph(attributePaths = {"product", "unit", "saleItem"})
    List<ProductionJpaEntity> findByParentProductionIdOrderByCreatedAtDescIdDesc(UUID parentProductionId);
}
