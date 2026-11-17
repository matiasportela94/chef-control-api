package com.chefcontrol.infrastructure.ai;

import com.chefcontrol.application.audio.AudioFormat;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.SpeechToTextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Sin OPENAI_API_KEY la app arranca igual, pero transcribir responde un error claro: nunca un texto inventado.
 * Condición inversa explícita y no {@code @ConditionalOnMissingBean}: en un componente escaneado, ese depende del
 * orden en que se leen las clases, y "NoOp…" se lee antes que "OpenAI…".
 */
@Slf4j
@Service
@ConditionalOnExpression("'${app.openai.api-key:}'.isEmpty()")
public class NoOpSpeechToTextService implements SpeechToTextService {

    @Override
    public String transcribe(byte[] audio, AudioFormat format) {
        log.warn("[AI no-op] transcribe called without OPENAI_API_KEY");
        throw new AppException(ErrorCode.AI_TRANSCRIPTION_NOT_CONFIGURED, "No speech-to-text provider configured");
    }
}
