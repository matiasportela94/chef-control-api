package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.history.PriceHistoryEntry;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "menu_item_price_history")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class MenuItemPriceHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "changed_by")
    private UUID changedBy;

    public static MenuItemPriceHistoryJpaEntity from(PriceHistoryEntry domain) {
        return MenuItemPriceHistoryJpaEntity.builder()
                .id(domain.id())
                .restaurantId(domain.restaurantId())
                .menuItemId(domain.entityId())
                .price(domain.value())
                .validFrom(domain.validFrom())
                .changedBy(domain.changedBy())
                .build();
    }

    public PriceHistoryEntry toDomain() {
        return new PriceHistoryEntry(id, restaurantId, menuItemId, price, validFrom, changedBy);
    }
}
