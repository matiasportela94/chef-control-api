package com.chefcontrol.domain.repository;

import com.chefcontrol.domain.sale.MenuItemAlias;

import java.util.List;
import java.util.UUID;

public interface MenuItemAliasRepository {

    List<MenuItemAlias> findByRestaurantId(UUID restaurantId);

    /** Por (local, texto impreso): el último que se confirmó gana, también para pasar de ignorado a un plato. */
    void upsert(MenuItemAlias alias);
}
