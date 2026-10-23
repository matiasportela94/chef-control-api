package com.chefcontrol.application.payments;

import com.chefcontrol.domain.payments.PaymentWebhookEvent;
import com.chefcontrol.domain.payments.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    public enum Outcome { ACCEPTED, DUPLICATE, REJECTED, NOT_CONFIGURED }

    private final WebhookVerifier verifier;
    private final PaymentWebhookEventRepository eventRepository;
    private final List<WebhookEventHandler> handlers;

    /**
     * Autentica el aviso, lo registra y se lo pasa a quien sepa actuar sobre él. Todo aviso
     * auténtico —incluso de un tipo que ningún handler conoce— se acepta, porque responder con
     * error haría que el proveedor lo reintente sin fin.
     *
     * <p>Registro y handler van en la misma transacción: si el handler falla se revierte también el
     * registro, el webhook contesta error y el reintento del proveedor vuelve a verse como nuevo.
     * Un aviso ya registrado (reintento de uno que sí se procesó) no se vuelve a procesar.
     */
    @Transactional
    public Outcome receivePaymentNotification(IncomingWebhook webhook) {
        if (!verifier.isConfigured()) {
            log.error("[PAYMENTS] {} webhook rejected: webhook secret is not configured", webhook.provider());
            return Outcome.NOT_CONFIGURED;
        }
        if (!verifier.isAuthentic(webhook)) {
            log.warn("[PAYMENTS] {} webhook rejected: invalid signature (requestId={})",
                    webhook.provider(), webhook.requestId());
            return Outcome.REJECTED;
        }

        String eventKey = firstNonBlank(webhook.eventId(), webhook.requestId(), UUID.randomUUID().toString());
        var event = new PaymentWebhookEvent(
                webhook.provider(), eventKey, webhook.topic(), webhook.dataId(), webhook.rawBody());
        boolean isNew = eventRepository.saveIfNew(event);

        log.info("[PAYMENTS] {} webhook {} topic={} dataId={}",
                webhook.provider(), isNew ? "stored" : "duplicate", webhook.topic(), webhook.dataId());
        if (!isNew) return Outcome.DUPLICATE;

        handlers.stream().filter(h -> h.handles(event)).forEach(h -> h.handle(event));
        return Outcome.ACCEPTED;
    }

    /** El último candidato nunca está en blanco (es un UUID recién generado). */
    private static String firstNonBlank(String first, String second, String last) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return last;
    }
}
