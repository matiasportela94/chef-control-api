package com.chefcontrol.api.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateStockCountRequest(
        String notes,
        @NotEmpty @Valid List<StockCountItemRequest> items
) {}
