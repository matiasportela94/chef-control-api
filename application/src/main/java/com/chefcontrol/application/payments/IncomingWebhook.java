package com.chefcontrol.application.payments;

/**
 * Un aviso HTTP de un proveedor de pago, sin interpretar. {@code signature}, {@code requestId} y
 * {@code dataId} son lo que el proveedor firma; {@code eventId} y {@code topic} salen del cuerpo
 * y solo sirven para registrar. Cualquiera puede ser {@code null}: el que llega mal se rechaza
 * al verificar la firma, no acá.
 */
public record IncomingWebhook(String provider, String signature, String requestId, String dataId,
                              String eventId, String topic, String rawBody) {
}
