package com.chefcontrol.infrastructure.persistence.jpa;

import com.chefcontrol.infrastructure.persistence.entity.MenuItemImageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaMenuItemImageRepository extends JpaRepository<MenuItemImageJpaEntity, UUID> {

    /** Sin los bytes: el listado solo necesita saber si hay foto y de cuando es. */
    @Query("""
            SELECT i.menuItemId, i.updatedAt FROM MenuItemImageJpaEntity i
            WHERE i.menuItemId IN :ids
            """)
    List<Object[]> findUpdatedAtByMenuItemIds(@Param("ids") Collection<UUID> ids);
}
