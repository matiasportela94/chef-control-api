package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.purchase.SupplierProductAlias;
import com.chefcontrol.domain.repository.SupplierProductAliasRepository;
import com.chefcontrol.infrastructure.persistence.entity.SupplierProductAliasJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaSupplierProductAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SupplierProductAliasRepositoryAdapter implements SupplierProductAliasRepository {

    private final JpaSupplierProductAliasRepository jpa;

    @Override
    public List<SupplierProductAlias> findBySupplierId(UUID supplierId) {
        return jpa.findBySupplierId(supplierId).stream().map(e -> new SupplierProductAlias(e.getId(), e.getRestaurantId(),
                e.getSupplierId(), e.getPrintedKey(), e.getProductId(), e.getUnitsPerPrintedUnit())).toList();
    }

    @Override
    public void upsert(SupplierProductAlias a) {
        SupplierProductAliasJpaEntity e = jpa.findBySupplierIdAndPrintedKey(a.supplierId(), a.printedKey())
                .orElseGet(SupplierProductAliasJpaEntity::new);
        // El factor aprendido se conserva si el insumo es el mismo y esta vez no vino otro.
        boolean sameProduct = a.productId().equals(e.getProductId());
        e.setRestaurantId(a.restaurantId());
        e.setSupplierId(a.supplierId());
        e.setPrintedKey(a.printedKey());
        e.setProductId(a.productId());
        if (a.unitsPerPrintedUnit() != null || !sameProduct) e.setUnitsPerPrintedUnit(a.unitsPerPrintedUnit());
        e.setUpdatedAt(Instant.now());
        jpa.save(e);
    }
}
