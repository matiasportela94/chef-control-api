package com.chefcontrol.domain.user;

import java.util.EnumSet;
import java.util.Set;

import static com.chefcontrol.domain.user.Permission.*;

public enum RoleName {
    OWNER, MANAGER, KITCHEN, READONLY;

    /**
     * Returns whether a user with {@code assignerRole} is allowed to assign this role to another user.
     * OWNER can assign any role. MANAGER can only assign non-privileged roles.
     */
    public boolean canBeAssignedBy(RoleName assignerRole) {
        if (assignerRole == OWNER) return true;
        if (assignerRole == MANAGER) return this == KITCHEN || this == READONLY;
        return false;
    }

    /**
     * Permisos de base para el rol, antes de aplicar los overrides puntuales del usuario
     * (ver PermissionOverride). Ajustable acá sin migración — cambia el default para todos
     * los usuarios de ese rol que no tengan un override explícito.
     */
    public Set<Permission> defaultPermissions() {
        return switch (this) {
            case OWNER, MANAGER -> EnumSet.allOf(Permission.class);
            case KITCHEN -> EnumSet.of(
                    AI_USE,
                    WASTE_VIEW, WASTE_MANAGE,
                    STOCK_VIEW,
                    STOCK_COUNTS_VIEW, STOCK_COUNTS_MANAGE,
                    MENU_VIEW,
                    PRODUCTS_VIEW,
                    PURCHASES_VIEW,
                    SALES_VIEW);
            case READONLY -> EnumSet.of(
                    PRODUCTS_VIEW, CATEGORIES_VIEW, SUPPLIERS_VIEW, PURCHASES_VIEW, SALES_VIEW,
                    WASTE_VIEW, STOCK_VIEW, STOCK_COUNTS_VIEW, MENU_VIEW, FOOD_COST_VIEW, ALERTS_VIEW);
        };
    }
}
