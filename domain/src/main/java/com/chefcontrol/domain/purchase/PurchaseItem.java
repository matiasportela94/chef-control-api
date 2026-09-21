package com.chefcontrol.domain.purchase;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    /** Del lote que creó esta línea, igual que {@link #quantityRemaining}: no es columna de purchase_items. */
    private BigDecimal quantityRemaining;
    private LocalDate expirationDate;
}
