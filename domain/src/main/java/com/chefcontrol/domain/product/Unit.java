package com.chefcontrol.domain.product;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor
public class Unit {

    private UUID id;
    private String name;
    private String abbreviation;
    private UnitType type;
    private UUID baseUnitId;
    private BigDecimal toBaseFactor;
    private boolean system;
}
