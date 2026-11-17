package com.chefcontrol.application.audio;

import java.util.Optional;

/**
 * El formato del audio por sus primeros bytes, no por el Content-Type que declara el cliente (mismo criterio que
 * {@code ImageFormat}). Solo los que se graban de verdad: webm/opus (Chrome y Android), mp4/aac (Safari en iOS) y
 * ogg/opus (las notas de voz de WhatsApp, Fase 3).
 */
public enum AudioFormat {

    WEBM("audio/webm", "webm"),
    MP4("audio/mp4", "m4a"),
    OGG("audio/ogg", "ogg");

    private final String contentType;
    private final String extension;

    AudioFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    /** El proveedor deduce el formato del nombre del archivo: hay que mandarlo con su extensión. */
    public String fileName() {
        return "audio." + extension;
    }

    public static Optional<AudioFormat> detect(byte[] data) {
        if (data == null || data.length < 12) return Optional.empty();
        if (is(data, 0, 0x1A, 0x45, 0xDF, 0xA3)) return Optional.of(WEBM);   // EBML (WebM/Matroska)
        if (is(data, 0, 'O', 'g', 'g', 'S')) return Optional.of(OGG);
        if (is(data, 4, 'f', 't', 'y', 'p')) return Optional.of(MP4);        // ....ftyp (ISO base media)
        return Optional.empty();
    }

    private static boolean is(byte[] data, int offset, int... magic) {
        for (int i = 0; i < magic.length; i++) {
            if ((data[offset + i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }
}
