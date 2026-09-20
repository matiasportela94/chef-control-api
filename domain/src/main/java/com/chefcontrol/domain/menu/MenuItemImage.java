package com.chefcontrol.domain.menu;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * La foto de un plato. Vive en su propia tabla para que el listado de platos no arrastre los
 * bytes: la grilla solo necesita saber si hay foto ({@code hasImage}) y cuándo cambió, para
 * romper la cache del navegador.
 */
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class MenuItemImage {

    private UUID menuItemId;
    private String contentType;
    private byte[] bytes;
    private int sizeBytes;
    private Instant updatedAt;
}
