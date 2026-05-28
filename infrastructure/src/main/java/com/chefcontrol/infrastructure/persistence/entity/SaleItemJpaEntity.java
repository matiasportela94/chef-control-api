package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.sale.SaleItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sale_items")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class SaleItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", insertable = false, updatable = false)
    private MenuItemJpaEntity menuItem;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    public static SaleItemJpaEntity from(SaleItem domain) {
        return SaleItemJpaEntity.builder()
                .id(domain.getId())
                .saleId(domain.getSaleId())
                .menuItemId(domain.getMenuItemId())
                .quantity(domain.getQuantity())
                .unitPrice(domain.getUnitPrice())
                .build();
    }

    public SaleItem toDomain() {
        return SaleItem.builder()
                .id(id)
                .saleId(saleId)
                .menuItemId(menuItemId)
                .menuItemName(menuItem != null ? menuItem.getName() : null)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
    }
}
