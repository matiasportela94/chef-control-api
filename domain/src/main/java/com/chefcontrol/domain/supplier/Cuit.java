package com.chefcontrol.domain.supplier;

import java.util.Optional;

/**
 * El CUIT: 11 dígitos con dígito verificador (módulo 11). Se guarda sin guiones. Validarlo ataja una mala lectura de la
 * IA en una factura: un dígito cambiado casi nunca pasa el verificador.
 */
public final class Cuit {

    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private Cuit() {}

    /** Los 11 dígitos si es un CUIT válido ("30-71234567-1", "30712345671"); vacío si no. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) return Optional.empty();
        String digits = raw.replaceAll("[\\s.\\-/]", "");
        if (!digits.matches("\\d{11}")) return Optional.empty();
        int sum = 0;
        for (int i = 0; i < 10; i++) sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        int check = 11 - sum % 11;
        if (check == 11) check = 0;
        if (check == 10) return Optional.empty();
        return check == digits.charAt(10) - '0' ? Optional.of(digits) : Optional.empty();
    }
}
