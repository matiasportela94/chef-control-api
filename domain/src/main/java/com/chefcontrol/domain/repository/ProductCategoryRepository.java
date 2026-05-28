package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.product.ProductCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository {

    List<ProductCategory> findAllByRestaurantIdOrderByName(UUID restaurantId);

    Optional<ProductCategory> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    ProductCategory save(ProductCategory category);

    void deleteById(UUID id);
}
