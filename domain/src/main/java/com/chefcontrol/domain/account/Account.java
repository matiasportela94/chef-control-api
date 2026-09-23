package com.chefcontrol.domain.account;

import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.plan.PlanTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Agrupa uno o más restaurantes bajo un mismo dueño. Es la entidad de facturación: el plan
 * se paga a nivel cuenta, no por restaurante — un restaurante no decide su propio plan.
 * Los roles también pertenecen a la cuenta (no al restaurante) — todos los restaurantes de
 * una cuenta comparten el mismo set de roles.
 * {@code ownerUserId} es la única verdad que no depende de ningún rol: esa persona siempre
 * tiene acceso total vía el rol de sistema (ver {@link com.chefcontrol.domain.user.Role#isSystem()}).
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Account {

    private UUID id;
    private UUID ownerUserId;
    private String name;
    private PlanTier plan;
    private Instant createdAt;

    public boolean hasFeature(Feature feature) {
        return plan.hasFeature(feature);
    }
}
