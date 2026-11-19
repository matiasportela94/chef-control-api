package com.chefcontrol.application.attachment;

import com.chefcontrol.application.image.ImageFormat;

import java.util.Optional;

/**
 * Lo que se acepta como factura o remito, detectado por magic bytes y no por el Content-Type que declara el cliente:
 * PDF, JPEG, PNG y WEBP. Nunca SVG (puede traer scripts) ni GIF.
 */
public enum AttachmentFormat {
    PDF("application/pdf"), JPEG("image/jpeg"), PNG("image/png"), WEBP("image/webp");

    private final String mediaType;

    AttachmentFormat(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    public boolean isPdf() {
        return this == PDF;
    }

    public static Optional<AttachmentFormat> detect(byte[] data) {
        if (data != null && data.length >= 5 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F'
                && data[4] == '-') {
            return Optional.of(PDF);
        }
        return ImageFormat.detect(data).flatMap(f -> switch (f) {
            case JPEG -> Optional.of(JPEG);
            case PNG -> Optional.of(PNG);
            case WEBP -> Optional.of(WEBP);
            default -> Optional.empty();
        });
    }
}
