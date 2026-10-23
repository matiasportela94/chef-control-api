package com.chefcontrol.application.payments;

import com.chefcontrol.domain.payments.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Puerto hacia un proveedor de pago recurrente. Una implementación por proveedor
 * (MercadoPago hoy): el resto del sistema no sabe de sus URLs, campos ni nombres de estado.
 * Toda falla de red o respuesta de error del proveedor sale como {@link PaymentProviderException}.
 */
public interface PaymentProvider {

    String MERCADOPAGO = "MERCADOPAGO";

    /** Identificador del proveedor tal como se guarda en {@code subscriptions.provider}. */
    String name();

    CreatedSubscription createSubscription(NewSubscription request);

    ProviderSubscription getSubscription(String providerSubscriptionId);

    /** Idempotente: cancelar una suscripción que el proveedor ya no tiene o ya canceló no es un error. */
    void cancelSubscription(String providerSubscriptionId);

    /** A qué suscripción pertenece un cobro: los avisos de cobro traen el id del cobro, no el de la suscripción. */
    String getChargeSubscriptionId(String providerChargeId);

    /**
     * @param externalReference nuestro id de la suscripción; vuelve en las consultas y permite
     *                          reconocer una suscripción del proveedor aunque no se haya podido guardar acá
     * @param backUrl           adónde devuelve el proveedor al cliente al terminar el checkout
     * @param frequencyMonths   cada cuántos meses se cobra el monto (1 mensual, 12 anual)
     */
    record NewSubscription(String externalReference, String reason, BigDecimal amountArs,
                           int frequencyMonths, String payerEmail, String backUrl) {}

    /** @param checkoutUrl adonde hay que redirigir al cliente para que cargue su tarjeta */
    record CreatedSubscription(String providerSubscriptionId, String checkoutUrl) {}

    /**
     * @param status          null si el proveedor devolvió un estado que no conocemos (ej. pausada): no se actúa sobre él
     * @param nextPaymentDate cuándo es el próximo cobro, o sea cuándo termina lo ya pagado; null si el proveedor no lo informa
     */
    record ProviderSubscription(String providerSubscriptionId, SubscriptionStatus status, Instant nextPaymentDate) {}
}
