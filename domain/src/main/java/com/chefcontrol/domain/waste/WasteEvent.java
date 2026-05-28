package com.chefcontrol.domain.waste;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class WasteEvent {

    private UUID id;
    private UUID restaurantId;
    private UUID productId;
    private String productName;
    private String productSku;
    private BigDecimal quantity;
    private UUID unitId;
    private String unitName;
    private String unitAbbreviation;
    private WasteReason reason;
    private BigDecimal cost;
    private UUID userId;
    private Instant createdAt;
}
