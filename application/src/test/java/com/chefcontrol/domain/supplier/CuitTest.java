package com.chefcontrol.domain.supplier;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CuitTest {

    @Test
    void aValidCuitIsNormalizedAndAMisreadDigitIsRejected() {
        assertEquals(Optional.of("20123456786"), Cuit.normalize("20-12345678-6"));
        assertEquals(Optional.of("30712345671"), Cuit.normalize("30 71234567 1"));
        assertEquals(Optional.empty(), Cuit.normalize("20-12345678-5"));   // un dígito mal leído
        assertEquals(Optional.empty(), Cuit.normalize("20-1234567-6"));
        assertEquals(Optional.empty(), Cuit.normalize(null));
    }
}
