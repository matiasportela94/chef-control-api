package com.chefcontrol.api.carta.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Alta y baja de platos en una carta — siempre en lote, sacar uno solo es una lista de uno. */
public record CartaItemsRequest(
        @NotEmpty List<UUID> menuItemIds
) {}
