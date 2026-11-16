package com.chefcontrol.application.payments;

/** El proveedor de pago no respondió o respondió con error. */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
