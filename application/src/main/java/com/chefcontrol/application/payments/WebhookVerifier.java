package com.chefcontrol.application.payments;

/**
 * Autentica avisos de un proveedor de pago. Una implementación por proveedor: el servicio no sabe
 * cómo firma cada uno.
 */
public interface WebhookVerifier {

    /** Falso si falta la clave secreta: sin ella no se puede autenticar nada y se rechaza todo. */
    boolean isConfigured();

    boolean isAuthentic(IncomingWebhook webhook);
}
