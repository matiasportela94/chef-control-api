package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.SupplierProductAliasJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaSupplierProductAliasRepository extends JpaRepository<SupplierProductAliasJpaEntity, UUID> {

    List<SupplierProductAliasJpaEntity> findBySupplierId(UUID supplierId);

    Optional<SupplierProductAliasJpaEntity> findBySupplierIdAndPrintedKey(UUID supplierId, String printedKey);
}
