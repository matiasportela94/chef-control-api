package com.chefcontrol.api.payments;

import com.chefcontrol.application.payments.IncomingWebhook;
import com.chefcontrol.application.payments.PaymentProvider;
import com.chefcontrol.application.payments.PaymentWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Entrada pública de los avisos de los proveedores de pago. No lleva JWT: lo que autentica el
 * pedido es la firma, que valida el servicio. Ver {@code SecurityConfig} (/webhooks/**).
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> receiveMercadoPagoNotification(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestBody(required = false) String rawBody) {

        String eventId = null, topic = null;
        try {
            JsonNode body = objectMapper.readTree(rawBody == null ? "" : rawBody);
            if (body.hasNonNull("id")) eventId = body.get("id").asText();
            if (body.hasNonNull("type")) topic = body.get("type").asText();
        } catch (Exception ignored) {
            // Cuerpo ilegible: si la firma es válida igual se registra tal cual y se contesta 200.
        }

        var outcome = paymentWebhookService.receivePaymentNotification(
                new IncomingWebhook(PaymentProvider.MERCADOPAGO, signature, requestId, dataId, eventId, topic, rawBody));

        return switch (outcome) {
            case ACCEPTED, DUPLICATE -> ResponseEntity.ok().build();
            case REJECTED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            case NOT_CONFIGURED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        };
    }
}
