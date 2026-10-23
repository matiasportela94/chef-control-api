package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.payments.BillingPeriod;
import com.chefcontrol.domain.payments.Subscription;
import com.chefcontrol.domain.payments.SubscriptionStatus;
import com.chefcontrol.domain.plan.PlanTier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter @Setter @NoArgsConstructor
public class SubscriptionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", nullable = false)
    private PlanTier planTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false)
    private BillingPeriod billingPeriod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_subscription_id")
    private String providerSubscriptionId;

    @Column(name = "amount_ars", nullable = false)
    private BigDecimal amountArs;

    @Column(name = "payer_email", nullable = false)
    private String payerEmail;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

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

    public static SubscriptionJpaEntity from(Subscription domain) {
        SubscriptionJpaEntity e = new SubscriptionJpaEntity();
        e.setId(domain.getId());
        e.setAccountId(domain.getAccountId());
        e.setPlanTier(domain.getPlanTier());
        e.setBillingPeriod(domain.getBillingPeriod());
        e.setStatus(domain.getStatus());
        e.setProvider(domain.getProvider());
        e.setProviderSubscriptionId(domain.getProviderSubscriptionId());
        e.setAmountArs(domain.getAmountArs());
        e.setPayerEmail(domain.getPayerEmail());
        e.setCurrentPeriodEnd(domain.getCurrentPeriodEnd());
        e.setCreatedAt(domain.getCreatedAt());
        e.setUpdatedAt(domain.getUpdatedAt());
        return e;
    }

    public Subscription toDomain() {
        return Subscription.builder()
                .id(id)
                .accountId(accountId)
                .planTier(planTier)
                .billingPeriod(billingPeriod)
                .status(status)
                .provider(provider)
                .providerSubscriptionId(providerSubscriptionId)
                .amountArs(amountArs)
                .payerEmail(payerEmail)
                .currentPeriodEnd(currentPeriodEnd)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
