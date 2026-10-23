package com.chefcontrol.api.account.dto;

import com.chefcontrol.application.service.AccountService.AccountOverview;
import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.plan.PlanTier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        /** El plan que rige HOY (si el pagado ya venció, TRIAL), no el guardado. */
        String plan,
        List<String> features,
        /** Si no es null, el plan actual deja de regir en ese instante y vuelve a TRIAL (suscripción cancelada). */
        Instant planExpiresAt,
        /** null = sin límite. */
        Integer restaurantLimit,
        int restaurantCount,
        int activeRestaurantCount,
        int userCount,
        String ownerName,
        String ownerEmail,
        Instant createdAt
) {
    public static AccountResponse from(AccountOverview o) {
        var account = o.account();
        Instant now = Instant.now();
        PlanTier effective = account.effectivePlan(now);
        return new AccountResponse(
                account.getId(),
                account.getName(),
                effective.name(),
                effective.getFeatures().stream().map(Feature::name).toList(),
                account.getPlanExpiresAt() != null && account.getPlanExpiresAt().isAfter(now)
                        ? account.getPlanExpiresAt() : null,
                account.hasFeature(Feature.MULTI_RESTAURANT) ? null : 1,
                o.restaurants().size(),
                (int) o.restaurants().stream().filter(r -> r.isActive()).count(),
                o.userCount(),
                o.owner() != null ? o.owner().getName() : null,
                o.owner() != null ? o.owner().getEmail() : null,
                account.getCreatedAt());
    }
}
