package com.chefcontrol.domain.purchase;

/**
 * El comprobante de una compra. A: el emisor es RI y discrimina IVA (precios sin IVA). B: con IVA incluido. C: el emisor
 * es monotributista y no discrimina IVA (con IVA incluido y alícuota 0: en un RI, ese IVA no se recupera y es costo).
 * REMITO: no es una factura; los precios, si trae, van con IVA.
 */
public enum InvoiceType {
    A, B, C, REMITO;

    public boolean pricesIncludeIva() {
        return this != A;
    }

    /** En una factura C no hay IVA discriminado: la alícuota de cada línea es 0. */
    public boolean ivaIsCost() {
        return this == C;
    }
}
