package com.chefcontrol.infrastructure.persistence.entity;

import com.chefcontrol.domain.payments.OneTimePayment;
import com.chefcontrol.domain.payments.OneTimePaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "one_time_payments")
@Getter @Setter @NoArgsConstructor
public class OneTimePaymentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "amount_ars", nullable = false)
    private BigDecimal amountArs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OneTimePaymentStatus status;

    @Column(name = "preference_id")
    private String preferenceId;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    private String target;

    @Column(name = "expires_at")
    private Instant expiresAt;

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

    public static OneTimePaymentJpaEntity from(OneTimePayment d) {
        OneTimePaymentJpaEntity e = new OneTimePaymentJpaEntity();
        e.setId(d.getId());
        e.setAccountId(d.getAccountId());
        e.setPurpose(d.getPurpose());
        e.setAmountArs(d.getAmountArs());
        e.setStatus(d.getStatus());
        e.setPreferenceId(d.getPreferenceId());
        e.setProviderPaymentId(d.getProviderPaymentId());
        e.setTarget(d.getTarget());
        e.setExpiresAt(d.getExpiresAt());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }

    public OneTimePayment toDomain() {
        return OneTimePayment.builder()
                .id(id).accountId(accountId).purpose(purpose).amountArs(amountArs).status(status)
                .preferenceId(preferenceId).providerPaymentId(providerPaymentId).target(target)
                .expiresAt(expiresAt).createdAt(createdAt).updatedAt(updatedAt)
                .build();
    }
}
