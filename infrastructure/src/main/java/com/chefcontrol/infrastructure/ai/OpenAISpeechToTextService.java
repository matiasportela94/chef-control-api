package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.audio.AudioFormat;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.SpeechToTextService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@code POST /v1/audio/transcriptions} de OpenAI. Modelo por defecto {@code gpt-transcribe} (USD 0,0045/min): el que
 * recomienda su guía desde que whisper-1 y los gpt-4o-*-transcribe quedaron deprecados (26/08/2026, se apagan el
 * 26/02/2027). Con gpt-transcribe el idioma va en {@code languages}, que reemplaza a {@code language}.
 *
 * <p><b>No probado contra OpenAI</b> (sin clave en local): solo contra un servidor simulado. A verificar en staging:
 * que acepte {@code languages[]} como campo del multipart y si la respuesta trae {@code usage}.
 */
@Slf4j
@Service
@ConditionalOnExpression("!'${app.openai.api-key:}'.isEmpty()")
public class OpenAISpeechToTextService implements SpeechToTextService {

    private final RestClient restClient;
    private final String model;

    public OpenAISpeechToTextService(@Value("${app.openai.api-key}") String apiKey,
                                     @Value("${app.openai.transcribe-model:gpt-transcribe}") String model,
                                     @Value("${app.openai.base-url:https://api.openai.com}") String baseUrl) {
        this.model = model;
        this.restClient = RestClient.builder()
                // Buffereado: sin esto el cliente de la JDK manda el multipart chunked, sin Content-Length.
                .requestFactory(new BufferingClientHttpRequestFactory(AiHttp.requestFactory()))
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public String transcribe(byte[] audio, AudioFormat format) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(format.contentType()));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return format.fileName();
            }
        }, fileHeaders));
        body.add("model", model);
        body.add("languages[]", "es");
        body.add("response_format", "json");

        long start = System.currentTimeMillis();
        JsonNode response;
        try {
            response = restClient.post().uri("/v1/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("[OpenAI] transcribe failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AppException(ErrorCode.AI_TRANSCRIPTION_FAILED, "Transcription failed");
        } catch (RestClientException e) {
            log.error("[OpenAI] transcribe failed: {}", e.getMessage());
            throw new AppException(ErrorCode.AI_TRANSCRIPTION_FAILED, "Transcription failed");
        }

        JsonNode usage = response == null ? null : response.path("usage");
        log.info("[OpenAI] transcribe model={} format={} bytes={} usage_type={} seconds={} input_tokens={} "
                        + "output_tokens={} ms={}", model, format, audio.length,
                usage == null ? null : usage.path("type").asText(null), usage == null ? null : usage.path("seconds").asText(null),
                usage == null ? null : usage.path("input_tokens").asText(null),
                usage == null ? null : usage.path("output_tokens").asText(null), System.currentTimeMillis() - start);

        String text = response == null ? null : response.path("text").asText(null);
        if (text == null) {
            log.error("[OpenAI] transcribe returned no text: {}", response);
            throw new AppException(ErrorCode.AI_TRANSCRIPTION_FAILED, "Transcription returned no text");
        }
        return text;
    }
}
