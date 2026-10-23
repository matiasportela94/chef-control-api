package com.chefcontrol.application.port;

import com.chefcontrol.domain.payments.BillingPeriod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Port for the transactional emails the business sends. Builds the content and hands it to an
 * {@link EmailSender}; knows nothing about SMTP or any provider.
 */
public interface EmailService {

    void sendPasswordResetLink(String toEmail, String userName, String resetLink);

    void sendSetPasswordLink(String toEmail, String userName, String restaurantName, String setPasswordLink);

    /**
     * La suscripción quedó activa. Sale al autorizarse, antes del primer cobro (MP lo hace hasta una hora
     * después), así que describe lo contratado y no confirma un pago: el comprobante de cada cobro lo manda MP.
     */
    void sendSubscriptionWelcome(String toEmail, String userName, String planName, BigDecimal amountArs,
                                 BillingPeriod billingPeriod, String appLink);

    /** Pagó durante la gracia de la mora: sigue con su plan. Cierra los recordatorios que recibió. */
    void sendPaymentRecovered(String toEmail, String userName, String planName, String appLink);

    /** Mora: el cobro del plan no entró; conserva el acceso hasta {@code accessUntil}. Va uno por día de gracia. */
    void sendPaymentFailedReminder(String toEmail, String userName, String planName, Instant accessUntil, String planLink);

    /** Mora vencida: la suscripción se canceló y la cuenta pasó a TRIAL. */
    void sendPlanDowngradedToTrial(String toEmail, String userName, String planName, String planLink);
}
