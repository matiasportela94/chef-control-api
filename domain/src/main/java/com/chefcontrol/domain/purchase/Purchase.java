package com.chefcontrol.domain.purchase;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Purchase {

    private UUID id;
    private UUID restaurantId;
    private UUID supplierId;
    private String supplierName;
    private UUID userId;
    private BigDecimal total;
    private String notes;
    private Instant purchasedAt;
    private Instant createdAt;
    private PurchaseStatus status;
}
