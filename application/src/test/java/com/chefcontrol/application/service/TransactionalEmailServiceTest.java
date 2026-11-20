package com.chefcontrol.application.service;

import com.chefcontrol.application.port.EmailMessage;
import com.chefcontrol.application.port.EmailSendException;
import com.chefcontrol.domain.payments.BillingPeriod;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionalEmailServiceTest {

    @Test
    void invitationEscapesUserControlledNamesAndKeepsLink() {
        List<EmailMessage> sent = new ArrayList<>();
        var service = new TransactionalEmailService(sent::add);

        service.sendSetPasswordLink("a@b.com", "Ana", "<script>x</script>", "https://app/set?token=1");

        EmailMessage m = sent.get(0);
        assertEquals("a@b.com", m.to());
        assertFalse(m.html().contains("<script>"));
        assertTrue(m.html().contains("&lt;script&gt;"));
        assertTrue(m.html().contains("https://app/set?token=1"));
        assertTrue(m.text().contains("https://app/set?token=1"));
        assertFalse(m.html().matches("(?s).*\\{[A-Z]+\\}.*"), "placeholder sin reemplazar");
    }

    @Test
    void addedToRestaurantMailEscapesNamesAndPointsToApp() {
        List<EmailMessage> sent = new ArrayList<>();
        var service = new TransactionalEmailService(sent::add);

        service.sendAddedToRestaurant("a@b.com", "<b>Ana</b>", "<script>x</script>", "https://app/login");

        EmailMessage m = sent.get(0);
        assertEquals("a@b.com", m.to());
        assertTrue(m.subject().contains("<script>x</script>"));
        assertFalse(m.html().contains("<b>Ana</b>"));
        assertTrue(m.html().contains("&lt;b&gt;Ana&lt;/b&gt;"));
        assertFalse(m.html().contains("<script>"));
        assertTrue(m.html().contains("&lt;script&gt;"));
        assertTrue(m.html().contains("https://app/login"));
        assertTrue(m.text().contains("https://app/login"));
        assertFalse(m.html().contains("no pasa nada hasta que uses el enlace"),
                "el acceso ya fue otorgado: el pie de enlaces de un solo uso no aplica");
        assertFalse(m.html().matches("(?s).*\\{[A-Z]+\\}.*"), "placeholder sin reemplazar");
    }

    @Test
    void paymentReminderShowsTheDeadlineInArgentineTimeWithTheBillingFooter() {
        List<EmailMessage> sent = new ArrayList<>();
        var service = new TransactionalEmailService(sent::add);

        service.sendPaymentFailedReminder("a@b.com", "Ana", "Starter",
                Instant.parse("2026-08-22T20:00:03Z"), "https://app/plan");

        EmailMessage m = sent.get(0);
        assertTrue(m.html().contains("22/08 a las 17:00"), "20:00 UTC son las 17:00 en Argentina");
        assertTrue(m.text().contains("22/08 a las 17:00"));
        assertTrue(m.html().contains("titular de la cuenta"));
        assertFalse(m.html().contains("Si no fuiste vos"), "el pie de los mails con enlace no aplica a un cobro");
        assertFalse(m.html().matches("(?s).*\\{[A-Z]+\\}.*"), "placeholder sin reemplazar");
    }

    @Test
    void welcomeShowsWhatIsChargedInArgentineFormat() {
        List<EmailMessage> sent = new ArrayList<>();
        var service = new TransactionalEmailService(sent::add);

        service.sendSubscriptionWelcome("a@b.com", "Ana", "Pro", new BigDecimal("1734000.00"),
                BillingPeriod.ANNUAL, "https://app/dashboard");

        EmailMessage m = sent.get(0);
        assertTrue(m.html().contains("$1.734.000 por año"), m.html());
        assertTrue(m.text().contains("$1.734.000 por año"));
        assertFalse(m.html().matches("(?s).*\\{[A-Z]+\\}.*"), "placeholder sin reemplazar");
    }

    @Test
    void insideATransactionTheMailWaitsForTheCommitAndARollbackSendsNothing() {
        List<EmailMessage> sent = new ArrayList<>();
        var service = new TransactionalEmailService(sent::add);
        try {
            TransactionSynchronizationManager.initSynchronization();
            service.sendPasswordResetLink("a@b.com", "Ana", "https://app/r");
            assertTrue(sent.isEmpty(), "no sale antes de confirmar");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertTrue(sent.isEmpty(), "revertida: no sale");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        try {
            TransactionSynchronizationManager.initSynchronization();
            service.sendPasswordResetLink("a@b.com", "Ana", "https://app/r");
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertEquals(1, sent.size(), "confirmada: sale una vez");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void failedSendDoesNotPropagate() {
        var service = new TransactionalEmailService(m -> { throw new EmailSendException("boom", null); });
        assertDoesNotThrow(() -> service.sendPasswordResetLink("a@b.com", "Ana", "https://app/r"));
    }
}
