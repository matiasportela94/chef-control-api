package com.chefcontrol.infrastructure.email;

import com.chefcontrol.application.port.EmailMessage;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmtpEmailSenderTest {

    /** Guarda el mensaje en vez de mandarlo. */
    static class CapturingSender extends JavaMailSenderImpl {
        MimeMessage sent;
        @Override public void send(MimeMessage message) { sent = message; }
    }

    private static String fromOf(String configuredFrom) throws Exception {
        CapturingSender mail = new CapturingSender();
        new SmtpEmailSender(mail, "cuenta@gmail.com", "clave", configuredFrom)
                .send(new EmailMessage("a@b.com", "asunto", "<p>html</p>", "texto"));
        return mail.sent.getFrom()[0].toString();
    }

    @Test
    void blankFromFallsBackToTheSmtpUser() throws Exception {
        // application.yml deja app.mail.from en "" cuando no hay MAIL_FROM.
        assertEquals("cuenta@gmail.com", fromOf(""));
        assertEquals("cuenta@gmail.com", fromOf("  "));
    }

    @Test
    void explicitFromWins() throws Exception {
        assertEquals("Chef Control <alias@gmail.com>", fromOf("Chef Control <alias@gmail.com>"));
    }
}
