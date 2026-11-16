package com.chefcontrol.infrastructure.payments;

import com.chefcontrol.application.payments.IncomingWebhook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los hex esperados se calcularon aparte con Python (hmac + sha256) sobre el manifiesto
 * del ejemplo de la doc de MP, así que el test no repite el algoritmo que prueba.
 */
class MercadoPagoWebhookVerifierTest {

    private static final String REQUEST_ID = "2066ca19-c6f1-498a-be75-1923005edd06";
    private static final String TS = "1742505638683";
    // id:ord01jq4s4ky8hwq6na5pxb65b3d3;request-id:...;ts:...;
    private static final String SIG_WITH_ID = "819e83180a7ac8c3c8f9c6c4535a232213932527180194d10f5cf95d11b6f0db";
    // request-id:...;ts:...;   (sin data.id)
    private static final String SIG_WITHOUT_ID = "cb219ff7688d11cdd38ef8002c7b9281a09d51004aa5fd490deee750a9bc0095";

    private final MercadoPagoWebhookVerifier verifier = new MercadoPagoWebhookVerifier("secret-de-prueba");

    private static IncomingWebhook webhook(String signature, String dataId) {
        return new IncomingWebhook("MERCADOPAGO", signature, REQUEST_ID, dataId, null, null, "{}");
    }

    @Test
    void acceptsValidSignatureAndLowercasesAlphanumericDataId() {
        // MP manda el id en mayúsculas en la URL pero firma el manifiesto con minúsculas
        assertTrue(verifier.isAuthentic(webhook("ts=" + TS + ",v1=" + SIG_WITH_ID, "ORD01JQ4S4KY8HWQ6NA5PXB65B3D3")));
    }

    @Test
    void omitsIdFromManifestWhenDataIdIsMissing() {
        assertTrue(verifier.isAuthentic(webhook("ts=" + TS + ",v1=" + SIG_WITHOUT_ID, null)));
    }

    @Test
    void rejectsTamperedOrMalformedSignatures() {
        assertFalse(verifier.isAuthentic(webhook("ts=" + TS + ",v1=" + SIG_WITH_ID, "OTRO-ID")));
        assertFalse(verifier.isAuthentic(webhook("ts=1,v1=" + SIG_WITH_ID, "ORD01JQ4S4KY8HWQ6NA5PXB65B3D3")));
        assertFalse(verifier.isAuthentic(webhook("v1=" + SIG_WITH_ID, "ORD01JQ4S4KY8HWQ6NA5PXB65B3D3")));
        assertFalse(verifier.isAuthentic(webhook("basura", "x")));
        assertFalse(verifier.isAuthentic(webhook(null, "x")));
    }

    @Test
    void withoutSecretNothingIsAuthenticAndItIsReportedAsNotConfigured() {
        var unconfigured = new MercadoPagoWebhookVerifier("");
        assertFalse(unconfigured.isConfigured());
        assertFalse(unconfigured.isAuthentic(webhook("ts=" + TS + ",v1=" + SIG_WITH_ID, "ORD01JQ4S4KY8HWQ6NA5PXB65B3D3")));
    }
}
