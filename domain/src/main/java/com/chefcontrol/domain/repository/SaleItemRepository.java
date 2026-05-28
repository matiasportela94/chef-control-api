package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.sale.SaleItem;

import java.util.List;
import java.util.UUID;

public interface SaleItemRepository {

    List<SaleItem> findBySaleId(UUID saleId);

    SaleItem save(SaleItem saleItem);
}
