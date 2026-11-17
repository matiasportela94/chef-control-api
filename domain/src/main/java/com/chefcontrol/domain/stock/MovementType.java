package com.chefcontrol.domain.stock;

public enum MovementType {
    PURCHASE, WASTE, SALE, ADJUSTMENT, REVERSAL, COUNT,
    /** OUT: un ingrediente que se consumió al producir. IN: entra el lote de la preparación. */
    PRODUCTION
}
