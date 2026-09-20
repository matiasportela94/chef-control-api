package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.account.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor
public class AccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static AccountJpaEntity from(Account domain) {
        AccountJpaEntity e = new AccountJpaEntity();
        e.setId(domain.getId());
        e.setOwnerUserId(domain.getOwnerUserId());
        e.setName(domain.getName());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public Account toDomain() {
        return Account.builder()
                .id(id)
                .ownerUserId(ownerUserId)
                .name(name)
                .createdAt(createdAt)
                .build();
    }
}
