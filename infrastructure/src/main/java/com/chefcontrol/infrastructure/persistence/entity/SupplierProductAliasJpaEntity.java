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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_product_aliases")
@Getter @Setter @NoArgsConstructor
public class SupplierProductAliasJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "printed_key", nullable = false)
    private String printedKey;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "units_per_printed_unit")
    private BigDecimal unitsPerPrintedUnit;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
