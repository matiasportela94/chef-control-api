package com.chefcontrol.domain.menu;

import com.chefcontrol.domain.exception.DomainException;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class MenuItem {

    private UUID id;
    private UUID restaurantId;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private boolean active = true;
    private Instant createdAt;

    public void deactivate() {
        this.active = false;
    }

    public void validateCanBeSold() {
        if (!active) throw new DomainException("Menu item '" + name + "' is not active");
        if (price == null) throw new DomainException("Menu item '" + name + "' has no price set");
    }
}
