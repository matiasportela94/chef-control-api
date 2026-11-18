package com.chefcontrol.domain.sale;

import java.util.UUID;

/**
 * Lo que imprime el POS del local → el plato. {@code menuItemId} null = "no es un plato" (bebidas, cubiertos, extras que
 * no están cargados): el próximo Z lo deja ignorado solo.
 *
 * @param printedKey el texto impreso normalizado (sin mayúsculas, acentos ni espacios de más)
 */
public record MenuItemAlias(UUID id, UUID restaurantId, String printedKey, UUID menuItemId) {

    public boolean ignored() {
        return menuItemId == null;
    }
}
