package com.chefcontrol.infrastructure.email;

import com.chefcontrol.application.port.EmailMessage;
import com.chefcontrol.application.port.EmailSendException;
import com.chefcontrol.application.port.EmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Staging and production: Railway Hobby blocks outbound SMTP (25/465/587), so mail goes through SendGrid's HTTPS API
 * ({@code POST /v3/mail/send}). Like {@link SmtpEmailSender}, it fails at startup without credentials: a send
 * failure is only logged. {@code MAIL_FROM} must be a sender verified in SendGrid, or every send is a 403.
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "sendgrid")
public class SendGridEmailSender implements EmailSender {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String apiKey;
    private final URI endpoint;
    private final Map<String, String> from;

    public SendGridEmailSender(ObjectMapper json,
                               @Value("${app.mail.sendgrid.api-key:}") String apiKey,
                               @Value("${app.mail.from:}") String from,
                               @Value("${app.mail.sendgrid.base-url:https://api.sendgrid.com}") String baseUrl) {
        if (apiKey.isBlank() || from.isBlank()) {
            throw new IllegalStateException("SendGrid mail needs SENDGRID_API_KEY and MAIL_FROM (a sender verified "
                    + "in SendGrid). Set them in the deploy environment.");
        }
        this.json = json;
        this.apiKey = apiKey.trim();
        this.endpoint = URI.create(baseUrl + "/v3/mail/send");
        this.from = parseFrom(from);
    }

    /** "Chef Control &lt;no-reply@x.com&gt;" works with SMTP; SendGrid wants the name and the address apart. */
    private static Map<String, String> parseFrom(String from) {
        try {
            InternetAddress address = new InternetAddress(from.trim(), true);
            Map<String, String> parsed = new LinkedHashMap<>();
            parsed.put("email", address.getAddress());
            if (address.getPersonal() != null) parsed.put("name", address.getPersonal());
            return parsed;
        } catch (AddressException e) {
            throw new IllegalStateException("MAIL_FROM is not a valid address: " + from, e);
        }
    }

    @Override
    public void send(EmailMessage message) {
        HttpResponse<String> response;
        try {
            // SendGrid exige text/plain antes que text/html.
            String body = json.writeValueAsString(Map.of(
                    "personalizations", List.of(Map.of("to", List.of(Map.of("email", message.to())))),
                    "from", from,
                    "subject", message.subject(),
                    "content", List.of(Map.of("type", "text/plain", "value", message.text()),
                            Map.of("type", "text/html", "value", message.html()))));
            response = http.send(HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("SendGrid send interrupted", e);
        } catch (Exception e) {
            throw new EmailSendException("SendGrid send failed: " + e, e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new EmailSendException("SendGrid send failed: " + response.statusCode() + " " + response.body(), null);
        }
    }
}
