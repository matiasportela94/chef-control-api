package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.product.Product;
import com.chefcontrol.domain.repository.ProductRepository;
import com.chefcontrol.domain.repository.ProductStockProjection;
import com.chefcontrol.domain.shared.Page;
import com.chefcontrol.domain.shared.PageRequest;
import com.chefcontrol.infrastructure.persistence.PersistenceUtils;
import com.chefcontrol.infrastructure.persistence.entity.ProductJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepository {

    private final JpaProductRepository jpa;

    @Override
    public Page<Product> findByRestaurantIdAndIsActiveTrue(UUID restaurantId, PageRequest pageRequest) {
        return PersistenceUtils.toDomain(
                jpa.findByRestaurantIdAndIsActiveTrue(restaurantId,
                        PersistenceUtils.toSpring(pageRequest, Sort.by("name").ascending()))
                   .map(ProductJpaEntity::toDomain));
    }

    @Override
    public Optional<Product> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(ProductJpaEntity::toDomain);
    }

    @Override
    public boolean existsByRestaurantIdAndSku(UUID restaurantId, String sku) {
        return jpa.existsByRestaurantIdAndSku(restaurantId, sku);
    }

    @Override
    public boolean existsByCategoryId(UUID categoryId) {
        return jpa.existsByCategoryId(categoryId);
    }

    @Override
    public long countByRestaurantIdAndIsActiveTrue(UUID restaurantId) {
        return jpa.countByRestaurantIdAndIsActiveTrue(restaurantId);
    }

    @Override
    public List<ProductStockProjection> findProductStockByRestaurant(UUID restaurantId) {
        return jpa.findProductStockByRestaurant(restaurantId);
    }

    @Override
    public Product save(Product product) {
        ProductJpaEntity saved = jpa.save(ProductJpaEntity.from(product));
        // Reload with @EntityGraph to populate category and defaultUnit relationships
        return jpa.findByIdAndRestaurantId(saved.getId(), saved.getRestaurantId())
                .map(ProductJpaEntity::toDomain)
                .orElse(saved.toDomain());
    }
}
