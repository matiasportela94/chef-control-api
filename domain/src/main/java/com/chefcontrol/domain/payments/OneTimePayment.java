package com.chefcontrol.domain.payments;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un pago único (no recurrente) de una cuenta. La pieza es genérica: {@code purpose} dice para qué es y {@code target}
 * lo que hay que aplicar al aprobarse, que interpreta quien maneja ese propósito (hoy, PLAN_UPGRADE con el plan destino).
 *
 * @param preferenceId      el checkout en el proveedor
 * @param providerPaymentId el pago en el proveedor, cuando hubo un intento
 * @param expiresAt         hasta cuándo se puede pagar el checkout
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OneTimePayment {

    public static final String PLAN_UPGRADE = "PLAN_UPGRADE";

    private UUID id;
    private UUID accountId;
    private String purpose;
    private BigDecimal amountArs;
    private OneTimePaymentStatus status;
    private String preferenceId;
    private String providerPaymentId;
    private String target;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
