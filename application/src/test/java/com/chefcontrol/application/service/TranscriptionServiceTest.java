package com.chefcontrol.application.service;

import com.chefcontrol.application.audio.AudioFormat;
import com.chefcontrol.application.exception.AppException;
import com.chefcontrol.application.exception.ErrorCode;
import com.chefcontrol.application.port.SpeechToTextService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TranscriptionServiceTest {

    private final SpeechToTextService stt = mock(SpeechToTextService.class);
    private final TranscriptionService service = new TranscriptionService(stt);

    static byte[] audio(int size, int... header) {
        byte[] data = new byte[size];
        for (int i = 0; i < header.length; i++) data[i] = (byte) header[i];
        return data;
    }

    static final byte[] WEBM = audio(64, 0x1A, 0x45, 0xDF, 0xA3);
    static final byte[] OGG = audio(64, 'O', 'g', 'g', 'S');
    static final byte[] MP4 = audio(64, 0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' ');

    @Test
    void theFormatComesFromTheMagicBytes() {
        assertEquals(Optional.of(AudioFormat.WEBM), AudioFormat.detect(WEBM));
        assertEquals(Optional.of(AudioFormat.OGG), AudioFormat.detect(OGG));
        assertEquals(Optional.of(AudioFormat.MP4), AudioFormat.detect(MP4));
        assertEquals(Optional.empty(), AudioFormat.detect(audio(64, 'I', 'D', '3')));  // mp3: no lo graba ningún navegador
        assertEquals(Optional.empty(), AudioFormat.detect(new byte[]{0x1A, 0x45}));
        assertEquals(Optional.empty(), AudioFormat.detect(null));
        assertEquals("audio.m4a", AudioFormat.MP4.fileName());
    }

    @Test
    void aValidAudioGoesToTheProviderWithItsFormat() {
        when(stt.transcribe(OGG, AudioFormat.OGG)).thenReturn("  se me quemaron 3 kg de pollo \n");

        assertEquals("se me quemaron 3 kg de pollo", service.transcribeAudio(OGG));
    }

    @Test
    void tooLargeOrUnknownIsRejectedWithoutCallingTheProvider() {
        byte[] big = Arrays.copyOf(WEBM, TranscriptionService.MAX_BYTES + 1);
        assertEquals(ErrorCode.AI_AUDIO_TOO_LARGE,
                assertThrows(AppException.class, () -> service.transcribeAudio(big)).getErrorCode());
        assertEquals(ErrorCode.AI_AUDIO_FORMAT_NOT_SUPPORTED,
                assertThrows(AppException.class, () -> service.transcribeAudio(audio(64, 'R', 'I', 'F', 'F'))).getErrorCode());
        assertEquals(ErrorCode.AI_AUDIO_FORMAT_NOT_SUPPORTED,
                assertThrows(AppException.class, () -> service.transcribeAudio(new byte[0])).getErrorCode());
        verifyNoInteractions(stt);
    }
}
