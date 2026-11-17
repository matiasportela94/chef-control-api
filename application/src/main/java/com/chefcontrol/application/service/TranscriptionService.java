package com.chefcontrol.application.service;

import com.chefcontrol.application.audio.AudioFormat;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.SpeechToTextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TranscriptionService {

    /**
     * Un techo por tamaño: la duración no se sabe sin parsear el contenedor. 2 min a 128 kbps son ~1,9 MB y el AAC de
     * Safari puede ir más alto. Tiene que ser ≤ spring.servlet.multipart.max-file-size.
     * ponytail: tope por bytes, no por duración. Un opus de 16 kbps de 4 MB son ~30 min (~USD 0,14 con gpt-transcribe);
     * si se abusa, medir la duración del contenedor o limitar pedidos por usuario.
     */
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    private final SpeechToTextService speechToTextService;

    public String transcribeAudio(byte[] audio) {
        if (audio != null && audio.length > MAX_BYTES) {
            throw AppException.badRequest(ErrorCode.AI_AUDIO_TOO_LARGE,
                    "The audio is larger than " + (MAX_BYTES / 1024 / 1024) + "MB");
        }
        AudioFormat format = AudioFormat.detect(audio).orElseThrow(() -> AppException.badRequest(
                ErrorCode.AI_AUDIO_FORMAT_NOT_SUPPORTED, "Only webm, mp4/m4a and ogg audio is supported"));
        return speechToTextService.transcribe(audio, format).strip();
    }
}
