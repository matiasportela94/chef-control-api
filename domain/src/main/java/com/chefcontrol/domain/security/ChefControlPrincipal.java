package com.chefcontrol.domain.security;

import java.util.UUID;

/**
 * Typed principal stored in the SecurityContext for every authenticated request.
 * Built from JWT claims in JwtAuthenticationFilter — no DB lookup needed.
 */
public record ChefControlPrincipal(
        UUID userId,
        String email,
        UUID activeRestaurantId,
        String role
) {}
