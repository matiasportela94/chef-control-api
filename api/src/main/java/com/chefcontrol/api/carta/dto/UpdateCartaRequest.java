package com.chefcontrol.api.carta.dto;

import jakarta.validation.constraints.Size;

public record UpdateCartaRequest(
        @Size(max = 255) String name,
        Boolean active
) {}
