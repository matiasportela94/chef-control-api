package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.supplier.Supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository {

    List<Supplier> findAllByRestaurantIdAndIsActiveTrueOrderByName(UUID restaurantId);

    Optional<Supplier> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Supplier save(Supplier supplier);
}
