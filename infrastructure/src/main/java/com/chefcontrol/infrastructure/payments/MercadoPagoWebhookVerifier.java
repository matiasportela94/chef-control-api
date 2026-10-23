package com.chefcontrol.infrastructure.payments;

import com.chefcontrol.application.payments.IncomingWebhook;
import com.chefcontrol.application.payments.WebhookVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Valida el header {@code x-signature} de MercadoPago ({@code ts=<n>,v1=<hex>}): HMAC-SHA256, en hex,
 * con la clave secreta de la aplicación, sobre el manifiesto
 * {@code id:<data.id en minúsculas>;request-id:<x-request-id>;ts:<ts>;}. Si algún valor no vino,
 * esa parte se omite del manifiesto. Formato según la doc oficial de MP (Webhooks).
 */
@Component
public class MercadoPagoWebhookVerifier implements WebhookVerifier {

    private final String secret;

    public MercadoPagoWebhookVerifier(@Value("${app.payments.mercadopago.webhook-secret:}") String secret) {
        this.secret = secret;
    }

    @Override
    public boolean isConfigured() {
        return !secret.isBlank();
    }

    @Override
    public boolean isAuthentic(IncomingWebhook webhook) {
        if (!isConfigured() || webhook.signature() == null) return false;

        String ts = null, v1 = null;
        for (String part : webhook.signature().split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("ts")) ts = kv[1];
            else if (kv[0].equals("v1")) v1 = kv[1];
        }
        if (ts == null || v1 == null) return false;

        StringBuilder manifest = new StringBuilder();
        if (notBlank(webhook.dataId())) manifest.append("id:").append(webhook.dataId().toLowerCase(Locale.ROOT)).append(';');
        if (notBlank(webhook.requestId())) manifest.append("request-id:").append(webhook.requestId()).append(';');
        manifest.append("ts:").append(ts).append(';');

        byte[] expected = hmacSha256Hex(manifest.toString()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256Hex(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
