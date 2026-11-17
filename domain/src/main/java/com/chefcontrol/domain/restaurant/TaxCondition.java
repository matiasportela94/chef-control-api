package com.chefcontrol.domain.restaurant;

/**
 * Condición frente al IVA del restaurante. Null = no cargada, y se comporta como monotributo: es la
 * opción que no cambia nada respecto de antes del IVA (el precio de compra entra entero al costo).
 */
public enum TaxCondition {
    RESPONSABLE_INSCRIPTO,
    MONOTRIBUTO;

    /** El RI recibe Factura A, que discrimina el IVA; el monotributista, la B, con el IVA adentro. */
    public static boolean pricesIncludeIvaByDefault(TaxCondition condition) {
        return condition != RESPONSABLE_INSCRIPTO;
    }

    /** El RI recupera el IVA de las compras (crédito fiscal): para él no es costo. Para el monotributista, sí. */
    public static boolean ivaIsCost(TaxCondition condition) {
        return condition != RESPONSABLE_INSCRIPTO;
    }
}
