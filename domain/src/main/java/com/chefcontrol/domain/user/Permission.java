package com.chefcontrol.domain.user;

/**
 * Catálogo fijo de permisos, uno por módulo (view/manage) salvo los de solo lectura.
 * Vive en código, no en DB — los roles no son editables, ver {@link RoleName#defaultPermissions()}.
 * Los overrides puntuales por usuario sí son dinámicos (ver PermissionOverride).
 */
public enum Permission {
    PRODUCTS_VIEW, PRODUCTS_MANAGE,
    CATEGORIES_VIEW, CATEGORIES_MANAGE,
    SUPPLIERS_VIEW, SUPPLIERS_MANAGE,
    PURCHASES_VIEW, PURCHASES_MANAGE,
    SALES_VIEW, SALES_MANAGE,
    WASTE_VIEW, WASTE_MANAGE,
    STOCK_VIEW, STOCK_MANAGE,
    STOCK_COUNTS_VIEW, STOCK_COUNTS_MANAGE,
    MENU_VIEW, MENU_MANAGE,
    FOOD_COST_VIEW,
    ALERTS_VIEW, ALERTS_MANAGE,
    USERS_VIEW, USERS_MANAGE,
    AUDIT_VIEW,
    AI_USE,
}
