package com.chefcontrol.application.port;

/**
 * Transport port: delivers one already-built message. Any mail provider (SMTP, SendGrid, ...)
 * implements this; the one in use is picked per environment with {@code app.mail.provider}.
 * Business emails (what to say) live in {@link EmailService}, not here.
 */
public interface EmailSender {

    /** @throws EmailSendException if the provider could not accept the message. */
    void send(EmailMessage message);
}
