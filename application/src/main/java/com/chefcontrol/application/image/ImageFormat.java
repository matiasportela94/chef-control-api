package com.chefcontrol.application.image;

import java.util.Optional;

/**
 * Detecta el formato mirando los primeros bytes del archivo, no el Content-Type que declara el
 * cliente. El Content-Type lo escribe quien sube: un .exe renombrado puede llegar diciendo
 * "image/png". Los magic bytes no mienten.
 *
 * Solo los formatos que todo navegador muestra. SVG queda afuera a propósito: es XML, puede
 * traer scripts, y servirlo desde nuestro dominio sería un XSS puesto por nosotros mismos.
 */
public enum ImageFormat {

    JPEG("image/jpeg", new int[]{0xFF, 0xD8, 0xFF}),
    PNG ("image/png",  new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    GIF ("image/gif",  new int[]{0x47, 0x49, 0x46, 0x38}),
    /** RIFF....WEBP — el magic no es contiguo, se chequea aparte. */
    WEBP("image/webp", new int[]{});

    private final String contentType;
    private final int[] magic;

    ImageFormat(String contentType, int[] magic) {
        this.contentType = contentType;
        this.magic = magic;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<ImageFormat> detect(byte[] data) {
        if (data == null) return Optional.empty();

        for (ImageFormat format : values()) {
            if (format.magic.length > 0 && startsWith(data, format.magic)) return Optional.of(format);
        }
        if (isWebp(data)) return Optional.of(WEBP);
        return Optional.empty();
    }

    private static boolean isWebp(byte[] data) {
        return data.length >= 12
                && startsWith(data, new int[]{0x52, 0x49, 0x46, 0x46})              // "RIFF"
                && (data[8] & 0xFF) == 'W' && (data[9] & 0xFF) == 'E'
                && (data[10] & 0xFF) == 'B' && (data[11] & 0xFF) == 'P';
    }

    private static boolean startsWith(byte[] data, int[] magic) {
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((data[i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }
}
