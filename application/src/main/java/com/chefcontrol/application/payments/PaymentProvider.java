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

    /** Lo que se cobra desde el próximo cobro (baja de plan, o la suba ya pagada). */
    void updateSubscriptionAmount(String providerSubscriptionId, BigDecimal amountArs);

    /** Desde cuándo no se cobra más (cambio de período); null la saca. */
    void setSubscriptionEndDate(String providerSubscriptionId, Instant endDate);

    /** Un checkout de pago único (no recurrente). */
    CreatedPayment createPayment(NewPayment request);

    ProviderPayment getPayment(String providerPaymentId);

    /**
     * @param externalReference nuestro id de la suscripción; vuelve en las consultas y permite
     *                          reconocer una suscripción del proveedor aunque no se haya podido guardar acá
     * @param backUrl           adónde devuelve el proveedor al cliente al terminar el checkout
     * @param frequencyMonths   cada cuántos meses se cobra el monto (1 mensual, 12 anual)
     * @param startDate         primer cobro (cambio de período); null = al autorizarse
     */
    record NewSubscription(String externalReference, String reason, BigDecimal amountArs,
                           int frequencyMonths, String payerEmail, String backUrl, Instant startDate) {}

    /** @param checkoutUrl adonde hay que redirigir al cliente para que cargue su tarjeta */
    record CreatedSubscription(String providerSubscriptionId, String checkoutUrl) {}

    /**
     * @param status          null si el proveedor devolvió un estado que no conocemos (ej. pausada): no se actúa sobre él
     * @param nextPaymentDate cuándo es el próximo cobro, o sea cuándo termina lo ya pagado; null si el proveedor no lo informa
     */
    record ProviderSubscription(String providerSubscriptionId, SubscriptionStatus status, Instant nextPaymentDate) {}

    /**
     * @param externalReference nuestro id del pago; vuelve en {@link ProviderPayment}
     * @param expiresAt         después de esto el checkout no se puede pagar
     */
    record NewPayment(String externalReference, String title, BigDecimal amountArs, String payerEmail,
                      String backUrl, Instant expiresAt) {}

    record CreatedPayment(String providerPreferenceId, String checkoutUrl) {}

    /** @param approved el proveedor lo acreditó; cualquier otro estado (pendiente, rechazado) es false */
    record ProviderPayment(String providerPaymentId, boolean approved, String externalReference,
                           BigDecimal amountArs, String currency) {}
}
