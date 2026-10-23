package com.chefcontrol.application.payments;

import com.chefcontrol.application.payments.PaymentWebhookService.Outcome;
import com.chefcontrol.domain.payments.PaymentWebhookEvent;
import com.chefcontrol.domain.payments.PaymentWebhookEventRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PaymentWebhookServiceTest {

    private final List<PaymentWebhookEvent> stored = new ArrayList<>();
    private final Set<String> keys = new HashSet<>();
    private final PaymentWebhookEventRepository repo = event -> {
        if (!keys.add(event.provider() + event.eventKey())) return false;
        stored.add(event);
        return true;
    };
    private final List<PaymentWebhookEvent> handled = new ArrayList<>();
    private final WebhookEventHandler handlerOfSubscriptions = new WebhookEventHandler() {
        public boolean handles(PaymentWebhookEvent e) { return "subscription_preapproval".equals(e.topic()); }
        public void handle(PaymentWebhookEvent e) { handled.add(e); }
    };

    private static WebhookVerifier verifier(boolean configured, boolean authentic) {
        return new WebhookVerifier() {
            public boolean isConfigured() { return configured; }
            public boolean isAuthentic(IncomingWebhook w) { return authentic; }
        };
    }

    private PaymentWebhookService service(boolean configured, boolean authentic) {
        return new PaymentWebhookService(verifier(configured, authentic), repo, List.of(handlerOfSubscriptions));
    }

    private static IncomingWebhook webhook(String eventId, String topic) {
        return new IncomingWebhook("MERCADOPAGO", "sig", "req-1", "123", eventId, topic, "{\"id\":1}");
    }

    @Test
    void storesAuthenticEventsEvenOfUnknownTopicAndSkipsRepeats() {
        var service = service(true, true);

        assertEquals(Outcome.ACCEPTED, service.receivePaymentNotification(webhook("evt-1", "card_updater")));
        assertEquals(Outcome.DUPLICATE, service.receivePaymentNotification(webhook("evt-1", "card_updater")));
        assertEquals(Outcome.ACCEPTED, service.receivePaymentNotification(webhook("evt-2", "card_updater")));
        assertEquals(2, stored.size());
        assertTrue(handled.isEmpty(), "ningún handler conoce card_updater: se registra y se acepta");
    }

    @Test
    void handlerRunsOncePerNewEventNeverOnRepeats() {
        var service = service(true, true);

        service.receivePaymentNotification(webhook("evt-1", "subscription_preapproval"));
        service.receivePaymentNotification(webhook("evt-1", "subscription_preapproval"));

        assertEquals(1, handled.size());
        assertEquals("123", handled.get(0).dataId());
    }

    @Test
    void aFailingHandlerPropagatesSoTheWholeNoticeIsRetried() {
        var failing = new WebhookEventHandler() {
            public boolean handles(PaymentWebhookEvent e) { return true; }
            public void handle(PaymentWebhookEvent e) { throw new PaymentProviderException("MP down"); }
        };
        var service = new PaymentWebhookService(verifier(true, true), repo, List.of(failing));

        assertThrows(PaymentProviderException.class,
                () -> service.receivePaymentNotification(webhook("evt-1", "subscription_preapproval")));
    }

    @Test
    void rejectedEventsAreNeverStoredNorHandled() {
        assertEquals(Outcome.REJECTED,
                service(true, false).receivePaymentNotification(webhook("evt-1", "subscription_preapproval")));
        assertEquals(Outcome.NOT_CONFIGURED,
                service(false, true).receivePaymentNotification(webhook("evt-1", "subscription_preapproval")));
        assertTrue(stored.isEmpty());
        assertTrue(handled.isEmpty());
    }

    @Test
    void fallsBackToRequestIdWhenBodyHasNoEventId() {
        service(true, true).receivePaymentNotification(webhook(null, "card_updater"));
        assertEquals("req-1", stored.get(0).eventKey());
    }
}
