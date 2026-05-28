package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.user.RoleEntity;
import com.chefcontrol.domain.user.RoleName;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter @Setter @NoArgsConstructor
public class RoleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private RoleName name;

    public static RoleJpaEntity from(RoleEntity domain) {
        RoleJpaEntity e = new RoleJpaEntity();
        e.setId(domain.getId());
        e.setName(domain.getName());
        return e;
    }

    public RoleEntity toDomain() {
        RoleEntity r = new RoleEntity();
        r.setId(id);
        r.setName(name);
        return r;
    }
}
