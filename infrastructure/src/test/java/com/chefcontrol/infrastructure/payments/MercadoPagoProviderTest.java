package com.chefcontrol.infrastructure.payments;

import com.chefcontrol.application.payments.PaymentProvider.NewPayment;
import com.chefcontrol.application.payments.PaymentProvider.NewSubscription;
import com.chefcontrol.application.payments.PaymentProviderException;
import com.chefcontrol.domain.payments.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Contra un servidor simulado: prueba lo que ENVIAMOS y cómo leemos la respuesta, no a MercadoPago. */
class MercadoPagoProviderTest {

    private static final String BASE = "https://mp.test";

    private record Fixture(MercadoPagoProvider provider, MockRestServiceServer server) {}

    private static Fixture fixture(String token) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new MercadoPagoProvider(builder, BASE, token, "https://api.test/"), server);
    }

    @Test
    void createSendsAPendingMonthlySubscriptionInArsAndReadsTheCheckoutLink() {
        var f = fixture("TOKEN");
        f.server.expect(requestTo(BASE + "/preapproval"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TOKEN"))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.external_reference").value("sub-1"))
                .andExpect(jsonPath("$.payer_email").value("a@b.com"))
                .andExpect(jsonPath("$.back_url").value("https://app/plan"))
                .andExpect(jsonPath("$.auto_recurring.frequency").value(1))
                .andExpect(jsonPath("$.auto_recurring.frequency_type").value("months"))
                .andExpect(jsonPath("$.auto_recurring.currency_id").value("ARS"))
                .andExpect(jsonPath("$.auto_recurring.transaction_amount").value(100000))
                .andRespond(withSuccess("{\"id\":\"mp-9\",\"init_point\":\"https://mp/checkout\",\"status\":\"pending\"}",
                        MediaType.APPLICATION_JSON));

        var created = f.provider.createSubscription(new NewSubscription(
                "sub-1", "Chef Control - Plan STARTER", new BigDecimal("100000"), 1, "a@b.com", "https://app/plan", null));

        assertEquals("mp-9", created.providerSubscriptionId());
        assertEquals("https://mp/checkout", created.checkoutUrl());
        f.server.verify();
    }

    @Test
    void mapsProviderStatesAndTreatsUnknownOnesAsNoAction() {
        assertEquals(SubscriptionStatus.ACTIVE, MercadoPagoProvider.mapStatus("authorized"));
        assertEquals(SubscriptionStatus.PENDING, MercadoPagoProvider.mapStatus("pending"));
        assertEquals(SubscriptionStatus.CANCELLED, MercadoPagoProvider.mapStatus("cancelled"));
        assertNull(MercadoPagoProvider.mapStatus("paused"));
        assertNull(MercadoPagoProvider.mapStatus(null));
    }

    @Test
    void annualSubscriptionSendsTwelveMonthsAsTheFrequency() {
        var f = fixture("TOKEN");
        f.server.expect(requestTo(BASE + "/preapproval"))
                .andExpect(jsonPath("$.auto_recurring.frequency").value(12))
                .andExpect(jsonPath("$.auto_recurring.frequency_type").value("months"))
                .andExpect(jsonPath("$.auto_recurring.transaction_amount").value(1020000))
                .andRespond(withSuccess("{\"id\":\"mp-9\",\"init_point\":\"https://mp/checkout\"}", MediaType.APPLICATION_JSON));

        f.provider.createSubscription(new NewSubscription(
                "sub-1", "Chef Control - Plan STARTER (anual)", new BigDecimal("1020000"), 12, "a@b.com", "https://app/plan", null));
        f.server.verify();
    }

    @Test
    void getReadsTheCurrentStatusAndTheNextPaymentDate() {
        var f = fixture("TOKEN");
        f.server.expect(requestTo(BASE + "/preapproval/mp-9"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"mp-9\",\"status\":\"authorized\","
                        + "\"next_payment_date\":\"2026-09-15T17:00:03.000-03:00\"}", MediaType.APPLICATION_JSON));

        var remote = f.provider.getSubscription("mp-9");

        assertEquals(SubscriptionStatus.ACTIVE, remote.status());
        assertEquals(java.time.Instant.parse("2026-09-15T20:00:03Z"), remote.nextPaymentDate());
    }

    @Test
    void aMissingOrUnreadableNextPaymentDateIsNull() {
        assertNull(MercadoPagoProvider.parseInstant(null));
        assertNull(MercadoPagoProvider.parseInstant("not a date"));
    }

    @Test
    void cancelSendsCancelledAndToleratesAnAlreadyGoneSubscription() {
        var f = fixture("TOKEN");
        f.server.expect(requestTo(BASE + "/preapproval/mp-9"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andRespond(withSuccess("{\"id\":\"mp-9\",\"status\":\"cancelled\"}", MediaType.APPLICATION_JSON));
        f.provider.cancelSubscription("mp-9");
        f.server.verify();

        var gone = fixture("TOKEN");
        gone.server.expect(requestTo(BASE + "/preapproval/mp-0")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertDoesNotThrow(() -> gone.provider.cancelSubscription("mp-0"));
    }

    @Test
    void aChargeIsTracedBackToItsSubscription() {
        var f = fixture("TOKEN");
        f.server.expect(requestTo(BASE + "/authorized_payments/6114264375"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":6114264375,\"preapproval_id\":\"mp-9\",\"status\":\"recycling\","
                        + "\"payment\":{\"id\":1,\"status\":\"rejected\"}}", MediaType.APPLICATION_JSON));

        assertEquals("mp-9", f.provider.getChargeSubscriptionId("6114264375"));

        var orphan = fixture("TOKEN");
        orphan.server.expect(requestTo(BASE + "/authorized_payments/1")).andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));
        assertThrows(PaymentProviderException.class, () -> orphan.provider.getChargeSubscriptionId("1"));
    }

    @Test
    void failuresBecomeProviderExceptionsAndTheTokenNeverLeaks() {
        var f = fixture("SECRET-TOKEN");
        f.server.expect(requestTo(BASE + "/preapproval/mp-9"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"message\":\"invalid\"}").contentType(MediaType.APPLICATION_JSON));
        var e = assertThrows(PaymentProviderException.class, () -> f.provider.getSubscription("mp-9"));
        assertFalse(e.getMessage().contains("SECRET-TOKEN"));

        assertThrows(PaymentProviderException.class, () -> fixture("").provider.getSubscription("mp-9"),
                "sin MP_ACCESS_TOKEN no se llama a MP");
    }

    // ---- cambio de plan y pagos únicos

    @Test
    void aSubscriptionWithAStartDateSendsItInArgentineTime() {
        var f = fixture("TOKEN");
        f.server.expect(requestTo(BASE + "/preapproval"))
                .andExpect(jsonPath("$.auto_recurring.start_date").value("2026-09-04T17:00:00.000-03:00"))
                .andRespond(withSuccess("{\"id\":\"mp-2\",\"init_point\":\"https://mp/c\"}", MediaType.APPLICATION_JSON));

        f.provider.createSubscription(new NewSubscription("sub-2", "Chef Control - Plan PRO (anual)",
                new BigDecimal("1734000"), 12, "a@b.com", "https://app/plan", Instant.parse("2026-09-04T20:00:00Z")));
        f.server.verify();
    }

    @Test
    void amountAndEndDateAreUpdatedOnTheRecurringPart() {
        var f = fixture("TOKEN");
        f.server.expect(requestTo(BASE + "/preapproval/mp-9"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.auto_recurring.transaction_amount").value(100000))
                .andExpect(jsonPath("$.auto_recurring.currency_id").value("ARS"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        f.server.expect(requestTo(BASE + "/preapproval/mp-9"))
                .andExpect(jsonPath("$.auto_recurring.end_date").value("2026-09-03T17:00:00.000-03:00"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        f.server.expect(requestTo(BASE + "/preapproval/mp-9"))
                .andExpect(content().json("{\"auto_recurring\":{\"end_date\":null}}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        f.provider.updateSubscriptionAmount("mp-9", new BigDecimal("100000"));
        f.provider.setSubscriptionEndDate("mp-9", Instant.parse("2026-09-03T20:00:00Z"));
        f.provider.setSubscriptionEndDate("mp-9", null);
        f.server.verify();
    }

    @Test
    void aOneTimePaymentIsACheckoutProPreferenceThatExpiresAndNotifiesUs() {
        var f = fixture("TOKEN");
        f.server.expect(requestTo(BASE + "/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.items[0].unit_price").value(33871))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].currency_id").value("ARS"))
                .andExpect(jsonPath("$.external_reference").value("pay-1"))
                .andExpect(jsonPath("$.payer.email").value("a@b.com"))
                .andExpect(jsonPath("$.back_urls.success").value("https://app/plan"))
                .andExpect(jsonPath("$.expires").value(true))
                .andExpect(jsonPath("$.expiration_date_to").value("2026-08-20T09:30:00.000-03:00"))
                .andExpect(jsonPath("$.notification_url").value("https://api.test/webhooks/mercadopago?source_news=webhooks"))
                .andRespond(withSuccess("{\"id\":\"pref-1\",\"init_point\":\"https://mp/pay\"}", MediaType.APPLICATION_JSON));

        var created = f.provider.createPayment(new NewPayment("pay-1", "Chef Control - Pasar al plan Pro",
                new BigDecimal("33871"), "a@b.com", "https://app/plan", Instant.parse("2026-08-20T12:30:00Z")));

        assertEquals("pref-1", created.providerPreferenceId());
        assertEquals("https://mp/pay", created.checkoutUrl());
        f.server.verify();
    }

    @Test
    void aPaymentIsReadWithItsReferenceAndAmount() {
        var f = fixture("TOKEN");
        f.server.expect(requestTo(BASE + "/v1/payments/123"))
                .andRespond(withSuccess("{\"id\":123,\"status\":\"approved\",\"external_reference\":\"pay-1\","
                        + "\"transaction_amount\":33871,\"currency_id\":\"ARS\"}", MediaType.APPLICATION_JSON));
        f.server.expect(requestTo(BASE + "/v1/payments/124"))
                .andRespond(withSuccess("{\"id\":124,\"status\":\"rejected\"}", MediaType.APPLICATION_JSON));

        var approved = f.provider.getPayment("123");
        assertTrue(approved.approved());
        assertEquals("pay-1", approved.externalReference());
        assertEquals(0, new BigDecimal("33871").compareTo(approved.amountArs()));
        assertEquals("ARS", approved.currency());
        assertFalse(f.provider.getPayment("124").approved());
    }
}
