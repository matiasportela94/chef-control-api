package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.user.Permission;
import com.chefcontrol.domain.user.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter @Setter @NoArgsConstructor
public class RoleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<Permission> permissions = new HashSet<>();

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static RoleJpaEntity from(Role domain) {
        RoleJpaEntity e = new RoleJpaEntity();
        e.setId(domain.getId());
        e.setAccountId(domain.getAccountId());
        e.setName(domain.getName());
        e.setSystem(domain.isSystem());
        // El rol de sistema no guarda filas en role_permissions — tiene todo siempre por código.
        e.setPermissions(domain.isSystem() ? Set.of() : new HashSet<>(domain.getPermissions()));
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public Role toDomain() {
        return Role.builder()
                .id(id)
                .accountId(accountId)
                .name(name)
                .isSystem(isSystem)
                .permissions(new HashSet<>(permissions))
                .createdAt(createdAt)
                .build();
    }
}
