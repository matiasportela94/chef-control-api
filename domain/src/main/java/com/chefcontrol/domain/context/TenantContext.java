package com.chefcontrol.domain.context;

import java.util.UUID;

public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_RESTAURANT = new ThreadLocal<>();

    public static void set(UUID restaurantId) {
        CURRENT_RESTAURANT.set(restaurantId);
    }

    public static UUID get() {
        return CURRENT_RESTAURANT.get();
    }

    public static UUID require() {
        UUID id = CURRENT_RESTAURANT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant in context — authenticated endpoint required");
        }
        return id;
    }

    public static void clear() {
        CURRENT_RESTAURANT.remove();
    }
}
