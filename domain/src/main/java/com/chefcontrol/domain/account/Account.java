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
 * Agrupa uno o más restaurantes bajo un mismo dueño. Es la entidad de facturación: el plan se paga
 * a nivel cuenta, no por restaurante — un restaurante no decide su propio plan.
 * Los roles también pertenecen a la cuenta (no al restaurante) — todos los restaurantes de
 * una cuenta comparten el mismo set de roles.
 * {@code ownerUserId} es la única verdad que no depende de ningún rol: esa persona siempre
 * tiene acceso total vía el rol de sistema (ver {@link com.chefcontrol.domain.user.Role#isSystem()}).
 *
 * <p>{@code plan} es lo guardado; lo que la cuenta puede usar HOY es {@link #effectivePlan(Instant)}.
 * Toda decisión de acceso pasa por ese, nunca por {@code getPlan()}.
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Account {

    private UUID id;
    private UUID ownerUserId;
    private String name;
    private PlanTier plan;
    /** El plan rige hasta este instante (exclusive); null = sin fecha de corte. Ver V29. */
    private Instant planExpiresAt;
    private Instant createdAt;

    /**
     * Plan que rige en {@code now}. Pasado {@code planExpiresAt} vuelve a TRIAL: el corte se calcula al
     * leer y no depende de que un job haya corrido, así que es exacto al segundo. Un plan que vence a las
     * 17:00:03 se puede usar hasta las 17:00:02.
     */
    public PlanTier effectivePlan(Instant now) {
        return planExpiresAt != null && !now.isBefore(planExpiresAt) ? PlanTier.TRIAL : plan;
    }

    public boolean hasFeature(Feature feature) {
        return effectivePlan(Instant.now()).hasFeature(feature);
    }
}
