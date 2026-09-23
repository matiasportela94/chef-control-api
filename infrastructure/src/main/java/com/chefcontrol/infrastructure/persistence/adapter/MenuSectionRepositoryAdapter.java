package com.chefcontrol.infrastructure.persistence.adapter;

import com.chefcontrol.domain.menu.MenuSection;
import com.chefcontrol.domain.repository.MenuSectionRepository;
import com.chefcontrol.infrastructure.persistence.entity.MenuSectionJpaEntity;
import com.chefcontrol.infrastructure.persistence.jpa.JpaMenuSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MenuSectionRepositoryAdapter implements MenuSectionRepository {

    private final JpaMenuSectionRepository jpa;

    @Override
    public List<MenuSection> findAllByRestaurantId(UUID restaurantId) {
        return jpa.findByRestaurantIdOrderBySortOrderAscNameAsc(restaurantId).stream()
                .map(MenuSectionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<MenuSection> findByIdAndRestaurantId(UUID id, UUID restaurantId) {
        return jpa.findByIdAndRestaurantId(id, restaurantId).map(MenuSectionJpaEntity::toDomain);
    }

    @Override
    public boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name) {
        return jpa.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name);
    }

    @Override
    public MenuSection save(MenuSection section) {
        return jpa.save(MenuSectionJpaEntity.from(section)).toDomain();
    }

    @Override
    public void delete(UUID id) {
        jpa.deleteById(id);
    }
}
