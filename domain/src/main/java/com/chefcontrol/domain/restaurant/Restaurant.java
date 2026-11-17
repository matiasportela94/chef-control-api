package com.chefcontrol.domain.restaurant;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.UUID;

/**
 * El plan/facturación vive en {@link com.chefcontrol.domain.account.Account}, no acá —
 * un restaurante no paga, la cuenta paga. Ver Account.hasFeature().
 */
@Getter @Setter @NoArgsConstructor
public class Restaurant {

    private UUID id;
    private UUID accountId;
    private String name;
    private String slug;
    private String timezone = "America/Argentina/Buenos_Aires";
    private boolean isActive = true;
    /** Null = no cargada; se comporta como monotributo (ver {@link TaxCondition}). */
    private TaxCondition taxCondition;
    /** La alícuota con la que vende. Solo cuenta si es RI: ver {@link #salesIvaRate()}. */
    private BigDecimal salesIvaPercentage = new BigDecimal("21");
    private Instant createdAt;

    /**
     * La alícuota con la que se separa el IVA de lo que vende. Solo el RI le debe al fisco el IVA de sus ventas
     * (débito fiscal); para el monotributista y quien no cargó la condición, el precio entero es ingreso: 0.
     */
    public BigDecimal salesIvaRate() {
        return taxCondition == TaxCondition.RESPONSABLE_INSCRIPTO ? salesIvaPercentage : BigDecimal.ZERO;
    }

    /**
     * Generates a URL-safe slug from a restaurant name.
     * Strips diacritics, lowercases, removes special chars, and replaces spaces with hyphens.
     * Uniqueness enforcement is the caller's responsibility.
     */
    public static String generateBaseSlug(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }
}
