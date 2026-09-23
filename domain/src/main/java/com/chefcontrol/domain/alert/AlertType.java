package com.chefcontrol.domain.alert;

public enum AlertType {
    LOW_STOCK,
    OVERSTOCK,
    EXPIRATION,
    PRICE_INCREASE,
    /** Se está tirando bastante más de lo que el producto pierde por limpieza. */
    WASTE_ABOVE_STANDARD
}
