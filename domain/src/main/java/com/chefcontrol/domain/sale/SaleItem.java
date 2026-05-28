package com.chefcontrol.domain.sale;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class SaleItem {

    private UUID id;
    private UUID saleId;
    private UUID menuItemId;
    private String menuItemName;
    private int quantity;
    private BigDecimal unitPrice;
}
