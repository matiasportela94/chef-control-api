package com.chefcontrol.api.payments.dto;

import com.chefcontrol.application.payments.SubscriptionService.Checkout;

/** @param checkoutUrl adonde el front redirige al cliente para que cargue su tarjeta */
public record CheckoutResponse(SubscriptionResponse subscription, String checkoutUrl) {
    public static CheckoutResponse from(Checkout checkout) {
        return new CheckoutResponse(SubscriptionResponse.from(checkout.subscription()), checkout.checkoutUrl());
    }
}
