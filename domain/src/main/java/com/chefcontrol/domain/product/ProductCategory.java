package com.chefcontrol.domain.product;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class ProductCategory {

    private UUID id;
    private UUID restaurantId;
    private String name;
    private String color;
    private Instant createdAt;
}
