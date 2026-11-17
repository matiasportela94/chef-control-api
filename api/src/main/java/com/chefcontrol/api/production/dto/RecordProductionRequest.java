package com.chefcontrol.api.production.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code quantity} en {@code unitId}, que tiene que convertir a la unidad de la preparación. */
public record RecordProductionRequest(
        @NotNull UUID productId,
        @NotNull @Positive BigDecimal quantity,
        @NotNull UUID unitId,
        LocalDate expirationDate,
        Instant producedAt,
        @Size(max = 1000) String notes
) {}
