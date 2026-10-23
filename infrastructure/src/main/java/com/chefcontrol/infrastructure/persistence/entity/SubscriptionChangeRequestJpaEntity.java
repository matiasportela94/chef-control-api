package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.payments.BillingPeriod;
import com.chefcontrol.domain.payments.ChangeRequestStatus;
import com.chefcontrol.domain.payments.SubscriptionChangeRequest;
import com.chefcontrol.domain.plan.PlanTier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_change_requests")
@Getter @Setter @NoArgsConstructor
public class SubscriptionChangeRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false)
    private PlanTier planTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false)
    private BillingPeriod billingPeriod;

    @Column(name = "amount_ars", nullable = false)
    private BigDecimal amountArs;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChangeRequestStatus status;

    @Column(name = "new_subscription_id")
    private UUID newSubscriptionId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public static SubscriptionChangeRequestJpaEntity from(SubscriptionChangeRequest d) {
        SubscriptionChangeRequestJpaEntity e = new SubscriptionChangeRequestJpaEntity();
        e.setId(d.getId());
        e.setAccountId(d.getAccountId());
        e.setSubscriptionId(d.getSubscriptionId());
        e.setPlanTier(d.getPlanTier());
        e.setBillingPeriod(d.getBillingPeriod());
        e.setAmountArs(d.getAmountArs());
        e.setEffectiveAt(d.getEffectiveAt());
        e.setStatus(d.getStatus());
        e.setNewSubscriptionId(d.getNewSubscriptionId());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }

    public SubscriptionChangeRequest toDomain() {
        return SubscriptionChangeRequest.builder()
                .id(id).accountId(accountId).subscriptionId(subscriptionId)
                .planTier(planTier).billingPeriod(billingPeriod).amountArs(amountArs)
                .effectiveAt(effectiveAt).status(status).newSubscriptionId(newSubscriptionId)
                .createdAt(createdAt).updatedAt(updatedAt)
                .build();
    }
}
