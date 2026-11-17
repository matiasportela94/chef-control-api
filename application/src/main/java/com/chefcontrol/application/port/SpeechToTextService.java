package com.chefcontrol.application.port;

import com.chefcontrol.application.audio.AudioFormat;

/**
 * Audio a texto. Lo usa el micrófono de la web y lo va a usar WhatsApp (Fase 3).
 *
 * <p>Si falla tira {@code AppException} con {@code AI_TRANSCRIPTION_FAILED}, o con {@code AI_TRANSCRIPTION_NOT_CONFIGURED}
 * si no hay proveedor. Nunca devuelve un texto inventado.
 */
public interface SpeechToTextService {

    /** El audio ya validado (tamaño y formato). Devuelve lo que se dijo, en castellano. */
    String transcribe(byte[] audio, AudioFormat format);
}
