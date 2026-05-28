package com.chefcontrol.domain.sale;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class Sale {

    private UUID id;
    private UUID restaurantId;
    private UUID userId;
    private BigDecimal totalAmount;
    private String source;
    private String notes;
    private Instant soldAt;
    private Instant createdAt;
}
