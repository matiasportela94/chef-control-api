package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.account.Account;
import com.chefcontrol.domain.plan.PlanTier;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanTier plan;

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
        e.setPlan(domain.getPlan());
        e.setCreatedAt(domain.getCreatedAt());
        return e;
    }

    public Account toDomain() {
        return Account.builder()
                .id(id)
                .ownerUserId(ownerUserId)
                .name(name)
                .plan(plan)
                .createdAt(createdAt)
                .build();
    }
}
