package com.chefcontrol.domain.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Excepción puntual al set de permisos por default del rol de un usuario, en un restaurante
 * dado. {@code granted=true} agrega un permiso que el rol no tiene por default;
 * {@code granted=false} le saca uno que el rol sí tiene por default.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class PermissionOverride {

    private UUID id;
    private UUID userId;
    private UUID restaurantId;
    private Permission permission;
    private boolean granted;
    private Instant createdAt;
}
