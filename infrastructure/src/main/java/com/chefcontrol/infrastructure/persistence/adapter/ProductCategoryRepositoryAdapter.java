package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.product.ProductCategory;
import com.chefcontrol.domain.repository.ProductCategoryRepository;
import com.chefcontrol.infrastructure.persistence.entity.ProductCategoryJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ProductCategoryRepositoryAdapter implements ProductCategoryRepository {

    private final JpaProductCategoryRepository jpa;

    @Override
    public List<ProductCategory> findAllAccessibleToRestaurant(UUID restaurantId) {
        return jpa.findAllAccessibleToRestaurant(restaurantId).stream()
                .map(ProductCategoryJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ProductCategory> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(ProductCategoryJpaEntity::toDomain);
    }

    @Override
    public Optional<ProductCategory> findByIdAccessibleTo(UUID id, UUID restaurantId) {
        return jpa.findByIdAccessibleTo(id, restaurantId).map(ProductCategoryJpaEntity::toDomain);
    }

    @Override
    public ProductCategory save(ProductCategory category) {
        return jpa.save(ProductCategoryJpaEntity.from(category)).toDomain();
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }
}
