package com.chefcontrol.domain.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Agrupa uno o más restaurantes bajo un mismo dueño. Los roles pertenecen a la cuenta
 * (no al restaurante) — todos los restaurantes de una cuenta comparten el mismo set de roles.
 * {@code ownerUserId} es la única verdad que no depende de ningún rol: esa persona siempre
 * tiene acceso total vía el rol de sistema (ver {@link com.chefcontrol.domain.user.Role#isSystem()}).
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Account {

    private UUID id;
    private UUID ownerUserId;
    private String name;
    private Instant createdAt;
}
