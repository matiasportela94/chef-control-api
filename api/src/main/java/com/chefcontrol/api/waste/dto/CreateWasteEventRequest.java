package com.chefcontrol.api.waste.dto;

import com.chefcontrol.domain.waste.WasteReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateWasteEventRequest(
        @NotNull UUID productId,
        @NotNull UUID unitId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        WasteReason reason,
        @DecimalMin("0") BigDecimal cost
) {}
