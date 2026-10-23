package com.chefcontrol.api.payments;

import com.chefcontrol.api.payments.dto.CheckoutResponse;
import com.chefcontrol.api.payments.dto.CreateSubscriptionRequest;
import com.chefcontrol.api.payments.dto.SubscriptionPlanResponse;
import com.chefcontrol.api.payments.dto.SubscriptionResponse;
import com.chefcontrol.application.payments.SubscriptionService;
import com.chefcontrol.domain.payments.BillingPeriod;
import com.chefcontrol.domain.plan.PlanTier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * Suscripción de pago de la cuenta actual. Crear y cancelar van sin {@code @PreAuthorize} por el
 * mismo motivo que cerrar o renombrar la cuenta: se factura la cuenta y no es un permiso que se
 * delegue; el servicio exige ser el dueño. Ver verlo sí es un permiso: {@code PERM_ACCOUNT_VIEW}.
 */
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /** 204 si la cuenta nunca se suscribió. */
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_ACCOUNT_VIEW')")
    public ResponseEntity<SubscriptionResponse> getSubscription() {
        return subscriptionService.getSubscription()
                .map(s -> ResponseEntity.ok(SubscriptionResponse.from(s)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Los planes que se pueden contratar (TRIAL no tiene precio), del más barato al más caro. */
    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('PERM_ACCOUNT_VIEW')")
    public List<SubscriptionPlanResponse> listSubscriptionPlans() {
        return Arrays.stream(PlanTier.values())
                .filter(tier -> subscriptionService.priceFor(tier, BillingPeriod.MONTHLY) != null)
                .map(tier -> new SubscriptionPlanResponse(tier,
                        subscriptionService.priceFor(tier, BillingPeriod.MONTHLY),
                        subscriptionService.priceFor(tier, BillingPeriod.ANNUAL),
                        tier.getFeatures().stream().map(Enum::name).toList()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<CheckoutResponse> createSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CheckoutResponse.from(subscriptionService.createSubscription(request.planTier(),
                        request.billingPeriod() == null ? BillingPeriod.MONTHLY : request.billingPeriod())));
    }

    @DeleteMapping
    public ResponseEntity<SubscriptionResponse> cancelSubscription() {
        return ResponseEntity.ok(SubscriptionResponse.from(subscriptionService.cancelSubscription()));
    }
}
