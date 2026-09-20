package com.chefcontrol.domain.user;

/**
 * Catálogo fijo de permisos — vive en código porque cada uno corresponde a un check real
 * en un controller, no se pueden inventar permisos que no hagan nada. Solo VIEW/CREATE/
 * UPDATE/DELETE que existan de verdad hoy — no se agregan de forma especulativa; cuando se
 * construya una acción nueva (ej. editar una merma), ahí se suma el permiso correspondiente.
 *
 * "Gestionar" no es un permiso — es un agrupador de UI que tilda CREATE+UPDATE+DELETE juntos.
 *
 * Quién puede tener estos permisos ya no es fijo (ver {@link Role}) — los roles se persisten
 * por cuenta y son editables, salvo el rol de sistema (SUPERADMIN) que siempre los tiene todos.
 */
public enum Permission {
    PRODUCTS_VIEW, PRODUCTS_CREATE, PRODUCTS_UPDATE, PRODUCTS_DELETE,
    CATEGORIES_VIEW, CATEGORIES_CREATE, CATEGORIES_UPDATE, CATEGORIES_DELETE,
    SUPPLIERS_VIEW, SUPPLIERS_CREATE, SUPPLIERS_UPDATE, SUPPLIERS_DELETE,
    PURCHASES_VIEW, PURCHASES_CREATE, PURCHASES_UPDATE, PURCHASES_DELETE,
    SALES_VIEW, SALES_CREATE, SALES_DELETE,
    WASTE_VIEW, WASTE_CREATE,
    STOCK_VIEW, STOCK_DELETE,
    STOCK_COUNTS_VIEW, STOCK_COUNTS_CREATE,
    MENU_VIEW, MENU_CREATE, MENU_UPDATE, MENU_DELETE,
    FOOD_COST_VIEW,
    ALERTS_VIEW, ALERTS_UPDATE,
    USERS_VIEW, USERS_CREATE, USERS_UPDATE, USERS_DELETE,
    AUDIT_VIEW,
    ROLES_VIEW, ROLES_CREATE, ROLES_UPDATE, ROLES_DELETE,
    RESTAURANTS_VIEW, RESTAURANTS_CREATE, RESTAURANTS_UPDATE, RESTAURANTS_DELETE,
    ACCOUNT_VIEW,
    AI_USE,
}
