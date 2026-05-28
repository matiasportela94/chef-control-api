package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.repository.SupplierRepository;
import com.chefcontrol.domain.supplier.Supplier;
import com.chefcontrol.infrastructure.persistence.entity.SupplierJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaSupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SupplierRepositoryAdapter implements SupplierRepository {

    private final JpaSupplierRepository jpa;

    @Override
    public List<Supplier> findAllByRestaurantIdAndIsActiveTrueOrderByName(UUID restaurantId) {
        return jpa.findAllByRestaurantIdAndIsActiveTrueOrderByName(restaurantId).stream()
                .map(SupplierJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Supplier> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(SupplierJpaEntity::toDomain);
    }

    @Override
    public Supplier save(Supplier supplier) {
        return jpa.save(SupplierJpaEntity.from(supplier)).toDomain();
    }
}
