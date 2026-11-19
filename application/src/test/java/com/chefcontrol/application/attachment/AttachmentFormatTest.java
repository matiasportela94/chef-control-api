package com.chefcontrol.application.attachment;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentFormatTest {

    @Test
    void theFormatComesFromTheBytesAndNeverAcceptsSvgOrGif() {
        assertEquals(Optional.of(AttachmentFormat.PDF), AttachmentFormat.detect("%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.of(AttachmentFormat.JPEG), AttachmentFormat.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertEquals(Optional.empty(), AttachmentFormat.detect("<svg onload=alert(1)>".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(Optional.empty(), AttachmentFormat.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII)));
    }
}
