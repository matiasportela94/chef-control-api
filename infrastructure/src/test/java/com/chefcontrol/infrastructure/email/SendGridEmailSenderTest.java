package com.chefcontrol.infrastructure.email;

import com.chefcontrol.application.port.EmailMessage;
import com.chefcontrol.application.port.EmailSendException;
import com.chefcontrol.application.port.EmailSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** Contra un servidor HTTP local de verdad: así se ve el pedido que sale. */
class SendGridEmailSenderTest {

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 202;
    private volatile String reply = "";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/mail/send", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
            if (out.length > 0) exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private SendGridEmailSender sender(String from) {
        return new SendGridEmailSender(json, "SG.secret-key", from, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static final EmailMessage MESSAGE = new EmailMessage("ana@example.com", "Restablecé tu contraseña",
            "<p>Hola Ána</p>", "Hola Ána");

    @Test
    void sendsTheMessageWithTheKeyAndTextBeforeHtml() throws Exception {
        sender("Chef Control <no-reply@chefcontrol.app>").send(MESSAGE);

        assertEquals("Bearer SG.secret-key", auth.get());
        assertEquals("application/json", contentType.get());
        JsonNode sent = json.readTree(body.get());
        assertEquals("ana@example.com", sent.at("/personalizations/0/to/0/email").asText());
        assertEquals("no-reply@chefcontrol.app", sent.at("/from/email").asText());
        assertEquals("Chef Control", sent.at("/from/name").asText());
        assertEquals("Restablecé tu contraseña", sent.at("/subject").asText());
        assertEquals("text/plain", sent.at("/content/0/type").asText());
        assertEquals("Hola Ána", sent.at("/content/0/value").asText());
        assertEquals("text/html", sent.at("/content/1/type").asText());
        assertEquals("<p>Hola Ána</p>", sent.at("/content/1/value").asText());
    }

    @Test
    void aBareFromGoesWithoutName() throws Exception {
        sender("no-reply@chefcontrol.app").send(MESSAGE);
        JsonNode from = json.readTree(body.get()).at("/from");
        assertEquals("no-reply@chefcontrol.app", from.path("email").asText());
        assertTrue(from.path("name").isMissingNode());
    }

    @Test
    void aNon2xxIsASendFailureWithStatusAndBodyButNotTheKey() {
        status = 401;
        reply = "{\"errors\":[{\"message\":\"The provided authorization grant is invalid, expired, or revoked\"}]}";
        EmailSendException e = assertThrows(EmailSendException.class, () -> sender("a@b.com").send(MESSAGE));
        assertThat(e.getMessage()).contains("401").contains("authorization grant is invalid")
                .doesNotContain("SG.secret-key");
    }

    @Test
    void anUnreachableServerIsASendFailure() {
        server.stop(0);
        assertThrows(EmailSendException.class, () -> sender("a@b.com").send(MESSAGE));
    }

    @Test
    void missingKeyOrFromOrABadFromFailsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new SendGridEmailSender(json, " ", "a@b.com", "http://x"));
        assertThrows(IllegalStateException.class, () -> new SendGridEmailSender(json, "SG.k", "", "http://x"));
        assertThrows(IllegalStateException.class, () -> new SendGridEmailSender(json, "SG.k", "no es un mail", "http://x"));
    }

    /** Como el component scan: withUserConfiguration elige constructor igual que en el arranque real. */
    private final ApplicationContextRunner beans = new ApplicationContextRunner()
            .withBean(ObjectMapper.class)
            .withBean(JavaMailSender.class, JavaMailSenderImpl::new)
            .withUserConfiguration(SmtpEmailSender.class, SendGridEmailSender.class);

    @Test
    void sendgridProviderCreatesSendGridWithoutAskingForSmtpCredentials() {
        beans.withPropertyValues("app.mail.provider=sendgrid", "app.mail.sendgrid.api-key=SG.k", "app.mail.from=a@b.com")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).getBean(EmailSender.class).isInstanceOf(SendGridEmailSender.class);
                    assertThat(ctx).doesNotHaveBean(SmtpEmailSender.class);
                });
    }

    @Test
    void withoutProviderItIsSmtp() {
        beans.withPropertyValues("spring.mail.username=u@gmail.com", "spring.mail.password=clave")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).getBean(EmailSender.class).isInstanceOf(SmtpEmailSender.class);
                });
    }

    @Test
    void sendgridWithoutKeyDoesNotStart() {
        beans.withPropertyValues("app.mail.provider=sendgrid", "app.mail.from=a@b.com")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("SENDGRID_API_KEY"));
    }
}
