package com.chefcontrol.api.payments;

import com.chefcontrol.api.payments.dto.ChangeSubscriptionRequest;
import com.chefcontrol.api.payments.dto.CheckoutResponse;
import com.chefcontrol.api.payments.dto.CreateSubscriptionRequest;
import com.chefcontrol.api.payments.dto.SubscriptionChangePreviewResponse;
import com.chefcontrol.api.payments.dto.SubscriptionChangeResponse;
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
                .map(d -> ResponseEntity.ok(SubscriptionResponse.from(d.subscription(), d.pendingChange())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Todos los planes, del gratis al más caro. Público (ver SecurityConfig): lo lee la landing sin sesión.
     * TRIAL viene con los precios en null —no se contrata— para que la landing liste sus features desde acá
     * y no desde una copia en el front que se desactualice.
     */
    @GetMapping("/plans")
    public List<SubscriptionPlanResponse> listSubscriptionPlans() {
        return Arrays.stream(PlanTier.values())
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

    /** Qué pasaría con el cambio, sin hacerlo. Solo el dueño, como el cambio mismo. */
    @GetMapping("/change-preview")
    public SubscriptionChangePreviewResponse previewSubscriptionChange(@RequestParam PlanTier planTier,
                                                                       @RequestParam BillingPeriod billingPeriod) {
        return SubscriptionChangePreviewResponse.from(subscriptionService.previewSubscriptionChange(planTier, billingPeriod));
    }

    /** Suba: el checkout del pago único. Baja o cambio de período: la solicitud programada (con checkout si es de período). */
    @PostMapping("/change")
    public SubscriptionChangeResponse requestSubscriptionChange(@Valid @RequestBody ChangeSubscriptionRequest request) {
        return SubscriptionChangeResponse.from(
                subscriptionService.requestSubscriptionChange(request.planTier(), request.billingPeriod()));
    }

    @DeleteMapping("/change")
    public SubscriptionResponse cancelSubscriptionChange() {
        return SubscriptionResponse.from(subscriptionService.cancelSubscriptionChange());
    }
}
