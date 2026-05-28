package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.SaleItemJpaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaSaleItemRepository extends JpaRepository<SaleItemJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"menuItem"})
    List<SaleItemJpaEntity> findBySaleId(UUID saleId);

    @EntityGraph(attributePaths = {"menuItem"})
    java.util.Optional<SaleItemJpaEntity> findWithRelationshipsById(UUID id);
}
