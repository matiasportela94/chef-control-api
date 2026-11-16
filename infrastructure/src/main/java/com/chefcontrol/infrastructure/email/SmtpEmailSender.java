package com.chefcontrol.infrastructure.email;

import com.chefcontrol.application.port.EmailMessage;
import com.chefcontrol.application.port.EmailSendException;
import com.chefcontrol.application.port.EmailSender;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Default provider. SMTP (Gmail with an app password) configured through {@code spring.mail.*}.
 * Fails at startup without credentials: a send failure is only logged, so a missing password
 * would otherwise mean silently undelivered mail.
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${spring.mail.username:}") String username,
                           @Value("${spring.mail.password:}") String password,
                           @Value("${app.mail.from:}") String from) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "SMTP mail needs MAIL_USERNAME and MAIL_PASSWORD (Gmail: an app password). "
                            + "Set them in .env / the deploy environment.");
        }
        this.mailSender = mailSender;
        // application.yml define app.mail.from como "" cuando falta MAIL_FROM: vacío = el usuario SMTP.
        this.from = from.isBlank() ? username : from;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);
        } catch (Exception e) {
            throw new EmailSendException("SMTP send failed", e);
        }
    }
}
