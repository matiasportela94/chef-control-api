package com.chefcontrol.domain.payments;

public enum BillingPeriod {
    MONTHLY(1),
    ANNUAL(12);

    private final int months;

    BillingPeriod(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
