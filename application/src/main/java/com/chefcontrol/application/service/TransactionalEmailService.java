package com.chefcontrol.application.service;

import com.chefcontrol.application.port.EmailMessage;
import com.chefcontrol.application.port.EmailSendException;
import com.chefcontrol.application.port.EmailSender;
import com.chefcontrol.application.port.EmailService;
import com.chefcontrol.domain.payments.BillingPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionalEmailService implements EmailService {

    private final EmailSender emailSender;

    @Override
    public void sendPasswordResetLink(String toEmail, String userName, String resetLink) {
        deliver(new EmailMessage(toEmail,
                "Restablecé tu contraseña de Chef Control",
                layout("Hola " + esc(userName) + ",",
                        "Recibimos un pedido para restablecer tu contraseña. El enlace vence pronto, así que usalo cuanto antes.",
                        "Restablecer contraseña", resetLink),
                "Hola " + userName + ",\n\nRestablecé tu contraseña acá: " + resetLink));
    }

    @Override
    public void sendSetPasswordLink(String toEmail, String userName, String restaurantName, String setPasswordLink) {
        deliver(new EmailMessage(toEmail,
                "Te invitaron a " + restaurantName + " en Chef Control",
                layout("Hola " + esc(userName) + ",",
                        "Te dieron acceso a <b>" + esc(restaurantName) + "</b>. Elegí tu contraseña para entrar.",
                        "Elegir contraseña", setPasswordLink),
                "Hola " + userName + ",\n\nTe dieron acceso a " + restaurantName
                        + ". Elegí tu contraseña acá: " + setPasswordLink));
    }

    @Override
    public void sendSubscriptionWelcome(String toEmail, String userName, String planName, BigDecimal amountArs,
                                        BillingPeriod billingPeriod, String appLink) {
        String charge = ars(amountArs) + (billingPeriod == BillingPeriod.ANNUAL ? " por año" : " por mes");
        deliver(new EmailMessage(toEmail,
                "Tu plan " + planName + " de Chef Control ya está activo",
                layout("Hola " + esc(userName) + ",",
                        "Te damos la bienvenida a Chef Control. Tu plan <b>" + esc(planName) + "</b> ya está activo: "
                                + "se cobra <b>" + charge + "</b> con el medio de pago que cargaste en Mercado Pago, "
                                + "que te va a mandar el comprobante de cada cobro.",
                        "Ir a Chef Control", appLink, BILLING_FOOTER),
                "Hola " + userName + ",\n\nTe damos la bienvenida a Chef Control. Tu plan " + planName
                        + " ya está activo: se cobra " + charge + " con Mercado Pago. Entrá acá: " + appLink));
    }

    @Override
    public void sendPlanChanged(String toEmail, String userName, String planName, BigDecimal amountArs,
                                BillingPeriod billingPeriod, String appLink) {
        String charge = ars(amountArs) + (billingPeriod == BillingPeriod.ANNUAL ? " por año" : " por mes");
        deliver(new EmailMessage(toEmail,
                "Tu plan de Chef Control cambió a " + planName,
                layout("Hola " + esc(userName) + ",",
                        "Tu cuenta ya tiene el plan <b>" + esc(planName) + "</b>. Desde ahora se cobra <b>" + charge
                                + "</b> con Mercado Pago, que te manda el comprobante de cada cobro.",
                        "Ir a Chef Control", appLink, BILLING_FOOTER),
                "Hola " + userName + ",\n\nTu cuenta ya tiene el plan " + planName + ". Desde ahora se cobra "
                        + charge + " con Mercado Pago. " + appLink));
    }

    @Override
    public void sendPaymentRecovered(String toEmail, String userName, String planName, String appLink) {
        deliver(new EmailMessage(toEmail,
                "Recibimos tu pago de Chef Control",
                layout("Hola " + esc(userName) + ",",
                        "Tu pago entró y seguís con tu plan <b>" + esc(planName) + "</b>, sin cortes. Gracias.",
                        "Ir a Chef Control", appLink, BILLING_FOOTER),
                "Hola " + userName + ",\n\nTu pago entró y seguís con tu plan " + planName + ". Gracias. " + appLink));
    }

    @Override
    public void sendPaymentFailedReminder(String toEmail, String userName, String planName, Instant accessUntil, String planLink) {
        String until = UNTIL.format(accessUntil);
        deliver(new EmailMessage(toEmail,
                "No pudimos cobrar tu plan de Chef Control",
                layout("Hola " + esc(userName) + ",",
                        "No pudimos cobrar tu plan <b>" + esc(planName) + "</b>. Mercado Pago va a volver a intentarlo; "
                                + "para no perder el acceso, revisá tu medio de pago antes del <b>" + until + "</b>. "
                                + "Pasada esa fecha la cuenta vuelve al plan gratis.",
                        "Revisar mi plan", planLink, BILLING_FOOTER),
                "Hola " + userName + ",\n\nNo pudimos cobrar tu plan " + planName
                        + ". Revisá tu medio de pago antes del " + until + " para no perder el acceso: " + planLink));
    }

    @Override
    public void sendPlanDowngradedToTrial(String toEmail, String userName, String planName, String planLink) {
        deliver(new EmailMessage(toEmail,
                "Tu cuenta de Chef Control pasó al plan gratis",
                layout("Hola " + esc(userName) + ",",
                        "No pudimos cobrar tu plan <b>" + esc(planName) + "</b> y la suscripción quedó cancelada, "
                                + "así que tu cuenta pasó al plan gratis. Tus datos siguen ahí: podés volver a "
                                + "suscribirte cuando quieras.",
                        "Volver a suscribirme", planLink, BILLING_FOOTER),
                "Hola " + userName + ",\n\nNo pudimos cobrar tu plan " + planName
                        + " y tu cuenta pasó al plan gratis. Podés volver a suscribirte acá: " + planLink));
    }

    // Los clientes son de Argentina: la fecha va en su hora, no en la del servidor (UTC en Railway).
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm")
            .withZone(ZoneId.of("America/Argentina/Buenos_Aires"));

    /** $170.000: pesos enteros, miles con punto. Locale explícito: el del JVM cambia según dónde se despliegue. */
    private static String ars(BigDecimal amount) {
        return "$" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-AR")).format(amount);
    }

    /**
     * A failed send is logged, not thrown: callers run inside a transaction (token creation) and
     * "forgot password" must answer the same whether or not the address exists, so an SMTP error
     * can neither roll back the token nor turn into a 500 that leaks existence.
     */
    private void deliver(EmailMessage message) {
        // Dentro de una transacción el mail sale recién cuando se confirma (decidido por Mati, 2026-10-12): si se
        // revierte no sale nada, y un reintento (el aviso de MP, la tarea de mora) no lo duplica. El token del
        // reset ya está guardado cuando llega el link.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(message);
                }
            });
            return;
        }
        send(message);
    }

    private void send(EmailMessage message) {
        try {
            emailSender.send(message);
        } catch (EmailSendException e) {
            log.error("Email to {} not delivered: {}", message.to(), e.getMessage(), e);
        }
    }

    // Dirección "A · Limpia" (docs/email-templates/a-limpia.html es la maqueta de referencia).
    // Tablas + estilos en línea porque Gmail/Outlook ignoran casi todo el CSS moderno; el logo es una
    // celda con una "C" (no una imagen) para que se vea aunque el cliente bloquee imágenes.
    private static final String FONT = "font-family:Poppins,system-ui,-apple-system,'Segoe UI',Arial,sans-serif";
    private static final String TEMPLATE = """
            <!doctype html><html lang="es"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="light">
            <link href="https://fonts.googleapis.com/css2?family=Poppins:wght@400;500;600;700&display=swap" rel="stylesheet">
            <style>body{margin:0;padding:0}@media(max-width:480px){.px{padding-left:20px!important;padding-right:20px!important}.btn{display:block!important;text-align:center!important}}</style>
            </head><body style="background:#ffffff"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 20px">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:600px">
            <tr><td class="px" style="padding:20px 32px"><table role="presentation" cellpadding="0" cellspacing="0"><tr>
              <td width="32" height="32" align="center" valign="middle" style="width:32px;height:32px;background:#F36525;border-radius:9px;color:#ffffff;{FONT};font-weight:700;font-size:19px;line-height:32px">C</td>
              <td style="padding-left:10px;{FONT};font-weight:600;font-size:16px;color:#1A1210">Chef Control</td></tr></table></td></tr>
            <tr><td style="border-top:1px solid #EDE8E2;font-size:0;line-height:0">&nbsp;</td></tr>
            <tr><td class="px" style="padding:36px 32px 28px">
              <h1 style="margin:0 0 16px;{FONT};font-weight:600;font-size:22px;line-height:30px;color:#1A1210">{GREETING}</h1>
              <p style="margin:0;{FONT};font-size:15px;line-height:24px;color:#3D3530">{BODY}</p>
              <div style="padding:24px 0"><a class="btn" href="{LINK}" style="display:inline-block;background:#F36525;color:#ffffff;{FONT};font-weight:600;font-size:15px;line-height:22px;text-decoration:none;padding:14px 28px;border-radius:8px">{CTA}</a></div>
              <p style="margin:0;{FONT};font-size:12px;line-height:19px;color:#9A9390">Si el botón no funciona, copiá este enlace en tu navegador:<br><a href="{LINK}" style="color:#9A9390;word-break:break-all">{LINK}</a></p>
            </td></tr>
            <tr><td style="border-top:1px solid #EDE8E2;font-size:0;line-height:0">&nbsp;</td></tr>
            <tr><td class="px" style="padding:20px 32px;{FONT};font-size:12px;line-height:18px;color:#9A9390">{FOOTER}<br>
              <b style="font-weight:500;color:#3D3530">Chef Control · Agente operativo para restaurantes</b></td></tr>
            </table></td></tr></table></body></html>
            """.replace("{FONT}", FONT);

    private static final String LINK_FOOTER = "Si no fuiste vos, ignorá este mensaje: no pasa nada hasta que uses el enlace.";
    private static final String BILLING_FOOTER = "Te escribimos porque sos el titular de la cuenta en Chef Control.";

    /** {@code greeting}/{@code body} may contain markup, so callers escape user data with {@link #esc}. */
    private static String layout(String greeting, String body, String cta, String link) {
        return layout(greeting, body, cta, link, LINK_FOOTER);
    }

    private static String layout(String greeting, String body, String cta, String link, String footer) {
        return TEMPLATE.replace("{GREETING}", greeting).replace("{BODY}", body)
                .replace("{CTA}", cta).replace("{LINK}", esc(link)).replace("{FOOTER}", footer);
    }

    /** User-controlled names go into HTML; escape them. */
    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
