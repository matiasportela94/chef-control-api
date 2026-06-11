package com.chefcontrol.domain.purchase;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PurchaseItem {

    private UUID id;
    private UUID purchaseId;
    private UUID productId;
    private String productName;
    private String productSku;
    private BigDecimal quantity;
    private UUID unitId;
    private String unitName;
    private String unitAbbreviation;
    private BigDecimal pricePerUnit;
    private Instant createdAt;
    private BigDecimal quantityRemaining;
}
