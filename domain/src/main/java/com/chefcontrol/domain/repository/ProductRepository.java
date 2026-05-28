package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Page<Product> findByRestaurantIdAndIsActiveTrue(UUID restaurantId, PageRequest pageRequest);

    Optional<Product> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndSku(UUID restaurantId, String sku);

    boolean existsByCategoryId(UUID categoryId);

    long countByRestaurantIdAndIsActiveTrue(UUID restaurantId);

    List<ProductStockProjection> findProductStockByRestaurant(UUID restaurantId);

    Product save(Product product);
}
