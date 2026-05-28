package com.chefcontrol.domain.stock;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class StockCount {

    private UUID id;
    private UUID restaurantId;
    private UUID userId;
    private String notes;
    private Instant countedAt = Instant.now();
}
