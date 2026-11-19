package com.chefcontrol.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "menu_item_aliases")
@Getter @Setter @NoArgsConstructor
public class MenuItemAliasJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "printed_key", nullable = false)
    private String printedKey;

    /** Null = ignorado: no es un plato. */
    @Column(name = "menu_item_id")
    private UUID menuItemId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
