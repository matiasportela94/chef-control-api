package com.chefcontrol.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "unit_conversions")
@Getter @NoArgsConstructor
public class UnitConversionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "from_unit_id", nullable = false)
    private UUID fromUnitId;

    @Column(name = "to_unit_id", nullable = false)
    private UUID toUnitId;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal factor;
}
