package com.chefcontrol.domain.supplier;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class Supplier {

    private UUID id;
    private UUID restaurantId;
    private String name;
    private ContactInfo contactInfo;
    private boolean isActive = true;
    private Instant createdAt;

    public void deactivate() {
        this.isActive = false;
    }
}
