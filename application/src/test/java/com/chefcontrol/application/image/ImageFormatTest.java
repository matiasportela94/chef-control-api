package com.chefcontrol.application.image;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ponytail: el único punto donde alguien puede mentir. El Content-Type lo escribe quien sube;
 * estos bytes son lo que de verdad llega.
 */
class ImageFormatTest {

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    @Test
    void detectsJpeg() {
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10)))
                .contains(ImageFormat.JPEG);
    }

    @Test
    void detectsPng() {
        assertThat(ImageFormat.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)))
                .contains(ImageFormat.PNG);
    }

    @Test
    void detectsGif() {
        assertThat(ImageFormat.detect("GIF89a...".getBytes(StandardCharsets.US_ASCII)))
                .contains(ImageFormat.GIF);
    }

    @Test
    void detectsWebpWithItsNonContiguousMagic() {
        byte[] webp = "RIFF____WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
        assertThat(ImageFormat.detect(webp)).contains(ImageFormat.WEBP);
    }

    @Test
    void rejectsRiffThatIsNotWebp() {
        // Un .wav también empieza con RIFF: el prefijo solo no alcanza.
        byte[] wav = "RIFF____WAVEfmt ".getBytes(StandardCharsets.US_ASCII);
        assertThat(ImageFormat.detect(wav)).isEmpty();
    }

    @Test
    void rejectsAnExecutableRenamedAsImage() {
        assertThat(ImageFormat.detect("MZ\u0090\u0000executable".getBytes(StandardCharsets.ISO_8859_1)))
                .isEmpty();
    }

    @Test
    void rejectsSvgBecauseItCanCarryScripts() {
        assertThat(ImageFormat.detect("<svg xmlns=\"...\"><script>".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
    }

    @Test
    void rejectsEmptyAndTruncated() {
        assertThat(ImageFormat.detect(null)).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8))).isEmpty(); // JPEG cortado
    }
}
