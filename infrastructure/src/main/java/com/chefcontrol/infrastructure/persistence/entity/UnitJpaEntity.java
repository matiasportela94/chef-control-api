package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.product.Unit;
import com.chefcontrol.domain.product.UnitType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "units")
@Getter @NoArgsConstructor
public class UnitJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String abbreviation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitType type;

    public static UnitJpaEntity from(Unit domain) {
        UnitJpaEntity e = new UnitJpaEntity();
        e.id = domain.getId();
        e.name = domain.getName();
        e.abbreviation = domain.getAbbreviation();
        e.type = domain.getType();
        return e;
    }

    public Unit toDomain() {
        Unit u = new Unit();
        u.setId(id);
        u.setName(name);
        u.setAbbreviation(abbreviation);
        u.setType(type);
        return u;
    }
}
