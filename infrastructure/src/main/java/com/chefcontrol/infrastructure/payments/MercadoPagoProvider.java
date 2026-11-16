package com.chefcontrol.infrastructure.payments;

import com.chefcontrol.application.payments.PaymentProvider;
import com.chefcontrol.application.payments.PaymentProviderException;
import com.chefcontrol.domain.payments.SubscriptionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Suscripciones de MercadoPago (API {@code /preapproval}) sin plan asociado y en estado
 * {@code pending}: el alta devuelve un {@code init_point} al que se redirige al cliente, que carga
 * su tarjeta en MercadoPago. Así la app nunca toca datos de tarjeta.
 *
 * <p>Verificado en la doc de MP: el alta (campos y respuesta {@code id}/{@code init_point}/{@code status}),
 * {@code GET /preapproval/{id}} y los estados {@code pending}/{@code authorized}/{@code cancelled}.
 * <b>No verificado contra la API real:</b> que cancelar sea {@code PUT /preapproval/{id}} con
 * {@code {"status":"cancelled"}} (la doc que pude leer lo describe de forma ambigua); cómo
 * reacciona MP a un {@code back_url} que no sea https; que {@code frequency: 12} con
 * {@code months} sea un cobro anual válido; y que el campo {@code next_payment_date} exista con ese
 * nombre y signifique "cuándo termina lo pagado" — de eso depende el acceso hasta fin de período.
 *
 * <p>Cambio de plan (2026-11-16), <b>tampoco verificado contra MP</b>: que {@code PUT /preapproval/{id}} con
 * {@code auto_recurring.transaction_amount} cambie el próximo cobro; que {@code auto_recurring.end_date} evite el
 * cobro siguiente y que mandarlo en null lo saque; que {@code start_date} a futuro no cobre al autorizar. Los pagos
 * únicos son Checkout Pro ({@code /checkout/preferences} y {@code /v1/payments/{id}}).
 */
@Component
public class MercadoPagoProvider implements PaymentProvider {

    /** MP espera fechas con milisegundos y offset ("2026-09-14T17:00:00.000-03:00"). */
    private static final DateTimeFormatter MP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneId.of("America/Argentina/Buenos_Aires"));

    private final RestClient http;
    private final String accessToken;
    private final String notificationUrl;

    public MercadoPagoProvider(RestClient.Builder builder,
                               @Value("${app.payments.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
                               @Value("${app.payments.mercadopago.access-token:}") String accessToken,
                               @Value("${app.backend.public-url:}") String backendPublicUrl) {
        this.http = builder.baseUrl(baseUrl).build();
        this.accessToken = accessToken;
        // Sin URL pública, el aviso del pago único depende de tener activado "Pagos" en el panel de MP.
        this.notificationUrl = backendPublicUrl.isBlank() ? null
                : backendPublicUrl.replaceAll("/+$", "") + "/webhooks/mercadopago?source_news=webhooks";
    }

    @Override
    public String name() {
        return MERCADOPAGO;
    }

    @Override
    public CreatedSubscription createSubscription(NewSubscription request) {
        Map<String, Object> autoRecurring = new LinkedHashMap<>();
        autoRecurring.put("frequency", request.frequencyMonths());
        autoRecurring.put("frequency_type", "months");
        autoRecurring.put("transaction_amount", request.amountArs());
        autoRecurring.put("currency_id", "ARS");
        if (request.startDate() != null) autoRecurring.put("start_date", MP_DATE.format(request.startDate()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", request.reason());
        body.put("external_reference", request.externalReference());
        body.put("payer_email", request.payerEmail());
        body.put("auto_recurring", autoRecurring);
        body.put("back_url", request.backUrl());
        body.put("status", "pending");

        JsonNode response = call("create subscription", () -> http.post().uri("/preapproval")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(JsonNode.class));

        String id = text(response, "id");
        String checkoutUrl = text(response, "init_point");
        if (id == null || checkoutUrl == null) {
            throw new PaymentProviderException("MercadoPago response without id/init_point");
        }
        return new CreatedSubscription(id, checkoutUrl);
    }

    @Override
    public ProviderSubscription getSubscription(String providerSubscriptionId) {
        JsonNode response = call("get subscription", () -> http.get().uri("/preapproval/{id}", providerSubscriptionId)
                .header("Authorization", bearer())
                .retrieve().body(JsonNode.class));
        return new ProviderSubscription(providerSubscriptionId, mapStatus(text(response, "status")),
                parseInstant(text(response, "next_payment_date")));
    }

    /**
     * {@code next_payment_date} es ISO-8601 con zona. null si falta o no se puede leer: el servicio
     * tiene una estimación de respaldo, pero el fin de período real SIEMPRE conviene que lo dé MP.
     */
    static Instant parseInstant(String iso) {
        if (iso == null) return null;
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public void cancelSubscription(String providerSubscriptionId) {
        try {
            call("cancel subscription", () -> http.put().uri("/preapproval/{id}", providerSubscriptionId)
                    .header("Authorization", bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", "cancelled"))
                    .retrieve().body(JsonNode.class));
        } catch (NotFound ignored) {
            // Ya no existe en MP: el resultado buscado (no se cobra más) ya está dado.
        }
    }

    /**
     * Un cobro de suscripción es un "authorized payment" en MP: {@code GET /authorized_payments/{id}}
     * trae {@code preapproval_id} (campos tomados de la referencia de la API, 2026-10-12; no probado contra MP).
     */
    @Override
    public String getChargeSubscriptionId(String providerChargeId) {
        JsonNode response = call("get charge", () -> http.get().uri("/authorized_payments/{id}", providerChargeId)
                .header("Authorization", bearer())
                .retrieve().body(JsonNode.class));
        String subscriptionId = text(response, "preapproval_id");
        if (subscriptionId == null) {
            throw new PaymentProviderException("MercadoPago charge " + providerChargeId + " without preapproval_id");
        }
        return subscriptionId;
    }

    @Override
    public void updateSubscriptionAmount(String providerSubscriptionId, BigDecimal amountArs) {
        updateAutoRecurring("update subscription amount", providerSubscriptionId,
                Map.of("transaction_amount", amountArs, "currency_id", "ARS"));
    }

    @Override
    public void setSubscriptionEndDate(String providerSubscriptionId, Instant endDate) {
        Map<String, Object> autoRecurring = new HashMap<>(); // admite null, Map.of no
        autoRecurring.put("end_date", endDate == null ? null : MP_DATE.format(endDate));
        updateAutoRecurring("set subscription end date", providerSubscriptionId, autoRecurring);
    }

    private void updateAutoRecurring(String what, String providerSubscriptionId, Map<String, Object> autoRecurring) {
        call(what, () -> http.put().uri("/preapproval/{id}", providerSubscriptionId)
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("auto_recurring", autoRecurring))
                .retrieve().body(JsonNode.class));
    }

    /** Checkout Pro: una preferencia con un solo ítem, que vence en {@code expiresAt}. */
    @Override
    public CreatedPayment createPayment(NewPayment request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", request.title());
        item.put("quantity", 1);
        item.put("currency_id", "ARS");
        item.put("unit_price", request.amountArs());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("payer", Map.of("email", request.payerEmail()));
        body.put("external_reference", request.externalReference());
        body.put("back_urls", Map.of("success", request.backUrl(), "pending", request.backUrl(), "failure", request.backUrl()));
        body.put("auto_return", "approved");
        body.put("expires", true);
        body.put("expiration_date_to", MP_DATE.format(request.expiresAt()));
        if (notificationUrl != null) body.put("notification_url", notificationUrl);

        JsonNode response = call("create payment", () -> http.post().uri("/checkout/preferences")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(JsonNode.class));

        String id = text(response, "id");
        String checkoutUrl = text(response, "init_point");
        if (id == null || checkoutUrl == null) {
            throw new PaymentProviderException("MercadoPago preference without id/init_point");
        }
        return new CreatedPayment(id, checkoutUrl);
    }

    @Override
    public ProviderPayment getPayment(String providerPaymentId) {
        JsonNode response = call("get payment", () -> http.get().uri("/v1/payments/{id}", providerPaymentId)
                .header("Authorization", bearer())
                .retrieve().body(JsonNode.class));
        String amount = text(response, "transaction_amount");
        return new ProviderPayment(providerPaymentId, "approved".equals(text(response, "status")),
                text(response, "external_reference"), amount == null ? null : new BigDecimal(amount),
                text(response, "currency_id"));
    }

    /** null = estado que no manejamos (ej. pausada): el servicio no actúa sobre él. */
    static SubscriptionStatus mapStatus(String mpStatus) {
        if (mpStatus == null) return null;
        return switch (mpStatus) {
            case "authorized" -> SubscriptionStatus.ACTIVE;
            case "pending" -> SubscriptionStatus.PENDING;
            case "cancelled" -> SubscriptionStatus.CANCELLED;
            default -> null;
        };
    }

    private String bearer() {
        if (accessToken.isBlank()) {
            throw new PaymentProviderException("MercadoPago access token is not configured (MP_ACCESS_TOKEN)");
        }
        return "Bearer " + accessToken;
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    /** Traduce cualquier falla de HTTP o de red a {@link PaymentProviderException}; el token nunca va al mensaje. */
    private static <T> T call(String what, java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) throw new NotFound(what, e);
            throw new PaymentProviderException("MercadoPago " + what + " failed: " + e.getStatusCode()
                    + " " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new PaymentProviderException("MercadoPago " + what + " failed: " + e.getMessage(), e);
        }
    }

    /** 404 del proveedor; solo {@code cancelSubscription} lo considera aceptable. */
    private static class NotFound extends PaymentProviderException {
        NotFound(String what, Throwable cause) {
            super("MercadoPago " + what + ": not found", cause);
        }
    }
}
