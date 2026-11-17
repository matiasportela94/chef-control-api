package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.audio.AudioFormat;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.SpeechToTextService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** Contra un servidor HTTP local de verdad: así se ve el multipart que sale, no una simulación de RestClient. */
class OpenAISpeechToTextServiceTest {

    private HttpServer server;
    private final AtomicReference<String> request = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "{\"text\":\"tres kilos de pollo\",\"usage\":{\"type\":\"duration\",\"seconds\":3.2}}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/audio/transcriptions", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(exchange.getRequestHeaders().getFirst("Content-Type") + "\n"
                    + "Content-Length: " + exchange.getRequestHeaders().getFirst("Content-Length") + "\n"
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAISpeechToTextService service() {
        return new OpenAISpeechToTextService("sk-test", "gpt-transcribe", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void sendsTheAudioAsAFileWithItsExtensionTheModelAndSpanish() {
        byte[] audio = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4, 5, 6, 7, 8};

        assertEquals("tres kilos de pollo", service().transcribe(audio, AudioFormat.WEBM));

        assertEquals("Bearer sk-test", auth.get());
        String sent = request.get();
        assertTrue(sent.startsWith("multipart/form-data"), sent);
        assertFalse(sent.contains("Content-Length: null"), "the upload goes with Content-Length, not chunked");
        assertTrue(sent.contains("name=\"file\"; filename=\"audio.webm\""), sent);
        assertTrue(sent.contains("Content-Type: audio/webm"), sent);
        assertTrue(sent.contains(new String(audio, StandardCharsets.ISO_8859_1)), "the audio bytes go as they are");
        assertTrue(sent.matches("(?s).*name=\"model\".*?\r\n\r\ngpt-transcribe\r\n.*"), sent);
        assertTrue(sent.matches("(?s).*name=\"languages\\[]\".*?\r\n\r\nes\r\n.*"), sent);
        assertTrue(sent.matches("(?s).*name=\"response_format\".*?\r\n\r\njson\r\n.*"), sent);
    }

    @Test
    void aProviderErrorOrAnAnswerWithoutTextIsTranscriptionFailedNeverAnInventedText() {
        status = 500;
        reply = "{\"error\":{\"message\":\"boom\"}}";
        assertEquals(ErrorCode.AI_TRANSCRIPTION_FAILED, assertThrows(AppException.class,
                () -> service().transcribe(new byte[12], AudioFormat.OGG)).getErrorCode());

        status = 200;
        reply = "{\"usage\":{\"type\":\"tokens\",\"input_tokens\":10}}";
        assertEquals(ErrorCode.AI_TRANSCRIPTION_FAILED, assertThrows(AppException.class,
                () -> service().transcribe(new byte[12], AudioFormat.OGG)).getErrorCode());
    }

    /** Local no tiene la clave: sin este test, un constructor que Spring no sabe elegir recién explota en staging. */
    @Test
    void theBeanIsTheOneOfTheKeyAndWithoutItTheNoOpSaysSo() {
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(OpenAISpeechToTextService.class, NoOpSpeechToTextService.class);
        runner.withPropertyValues("app.openai.api-key=relleno").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).getBean(SpeechToTextService.class).isInstanceOf(OpenAISpeechToTextService.class);
        });
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            SpeechToTextService noOp = ctx.getBean(SpeechToTextService.class);
            assertThat(noOp).isInstanceOf(NoOpSpeechToTextService.class);
            assertEquals(ErrorCode.AI_TRANSCRIPTION_NOT_CONFIGURED, assertThrows(AppException.class,
                    () -> noOp.transcribe(new byte[12], AudioFormat.WEBM)).getErrorCode());
        });
    }
}
