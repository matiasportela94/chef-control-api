package com.chefcontrol.domain.menu;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Un paso del menú: entradas, principales, postres, bebidas. Pertenece al restaurante, no a una
 * carta — todas las cartas del local comparten los mismos pasos y filtran por ellos.
 *
 * {@code sortOrder} es el orden en que se come, no el alfabético: entradas antes que postres.
 * {@code color} pinta la tarjeta del plato en /menu, para reconocer el paso de un vistazo.
 */
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class MenuSection {

    private UUID id;
    private UUID restaurantId;
    private String name;
    private int sortOrder;
    private String color;
    private String icon;
    private Instant createdAt;
}
