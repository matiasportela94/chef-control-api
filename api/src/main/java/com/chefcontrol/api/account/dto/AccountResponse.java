package com.chefcontrol.api.account.dto;

import com.chefcontrol.application.service.AccountService.AccountOverview;
import com.chefcontrol.domain.plan.Feature;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        String plan,
        List<String> features,
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
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getPlan().name(),
                account.getPlan().getFeatures().stream().map(Feature::name).sorted().toList(),
                account.hasFeature(Feature.MULTI_RESTAURANT) ? null : 1,
                o.restaurants().size(),
                (int) o.restaurants().stream().filter(r -> r.isActive()).count(),
                o.userCount(),
                o.owner() != null ? o.owner().getName() : null,
                o.owner() != null ? o.owner().getEmail() : null,
                account.getCreatedAt());
    }
}
