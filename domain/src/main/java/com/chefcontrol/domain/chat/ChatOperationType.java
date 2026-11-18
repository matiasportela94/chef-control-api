package com.chefcontrol.domain.chat;

import com.chefcontrol.domain.user.Permission;

/**
 * Lo que el chat de la IA registra en el acto, con deshacer. El id de la acción apunta a: PURCHASE → la compra,
 * WASTE → el waste_event, SALE → la venta, STOCK_ADJUSTMENT → el conteo, PRODUCTION → la producción.
 *
 * <p>{@code createPermission} es el de la pantalla de esa operación (el chat no es un atajo de permisos);
 * {@code undoPermission}, el de revertirla, que pide deshacer pasados los 15 minutos o para lo de otro.
 */
public enum ChatOperationType {
    PURCHASE(Permission.PURCHASES_CREATE, Permission.PURCHASES_DELETE),
    WASTE(Permission.WASTE_CREATE, Permission.STOCK_DELETE),
    SALE(Permission.SALES_CREATE, Permission.SALES_DELETE),
    STOCK_ADJUSTMENT(Permission.STOCK_COUNTS_CREATE, Permission.STOCK_DELETE),
    PRODUCTION(Permission.PRODUCTIONS_CREATE, Permission.PRODUCTIONS_DELETE);

    private final Permission createPermission;
    private final Permission undoPermission;

    ChatOperationType(Permission createPermission, Permission undoPermission) {
        this.createPermission = createPermission;
        this.undoPermission = undoPermission;
    }

    public Permission createPermission() {
        return createPermission;
    }

    public Permission undoPermission() {
        return undoPermission;
    }
}
