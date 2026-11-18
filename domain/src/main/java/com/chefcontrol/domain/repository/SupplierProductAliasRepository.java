package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.purchase.SupplierProductAlias;

import java.util.List;
import java.util.UUID;

public interface SupplierProductAliasRepository {

    List<SupplierProductAlias> findBySupplierId(UUID supplierId);

    /** Por (proveedor, texto impreso): el último que se confirmó gana. */
    void upsert(SupplierProductAlias alias);
}
