package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.product.ProductCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository {

    /** Returns system categories + the restaurant's own categories, ordered system-first then by name. */
    List<ProductCategory> findAllAccessibleToRestaurant(UUID restaurantId);

    /** Finds a category that belongs to this restaurant (excludes system categories). */
    Optional<ProductCategory> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    /** Finds a category accessible to this restaurant (system categories or own). */
    Optional<ProductCategory> findByIdAccessibleTo(UUID id, UUID restaurantId);

    ProductCategory save(ProductCategory category);

    void deleteById(UUID id);
}
