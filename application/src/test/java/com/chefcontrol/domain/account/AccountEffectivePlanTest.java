package com.chefcontrol.domain.account;

import com.chefcontrol.domain.plan.Feature;
import com.chefcontrol.domain.plan.PlanTier;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AccountEffectivePlanTest {

    // Pagó el 15/08 a las 17:00:03 (hora argentina, UTC-3) un mes: lo pagado termina el 15/09 a las 17:00:03.
    private static final Instant PERIOD_END = Instant.parse("2026-09-15T20:00:03Z");

    private static Account account(Instant expiresAt) {
        return Account.builder().plan(PlanTier.PRO).planExpiresAt(expiresAt).build();
    }

    @Test
    void theCutIsExactToTheSecond() {
        Account a = account(PERIOD_END);

        assertEquals(PlanTier.PRO, a.effectivePlan(Instant.parse("2026-09-15T20:00:02Z")),
                "hasta las 17:00:02 sigue con el plan pagado");
        assertEquals(PlanTier.TRIAL, a.effectivePlan(PERIOD_END), "a las 17:00:03 ya no");
        assertEquals(PlanTier.TRIAL, a.effectivePlan(Instant.parse("2026-12-01T00:00:00Z")));
    }

    @Test
    void withoutACutTheStoredPlanAlwaysApplies() {
        assertEquals(PlanTier.PRO, account(null).effectivePlan(Instant.parse("2030-01-01T00:00:00Z")));
    }

    @Test
    void featuresFollowTheEffectivePlan() {
        Account expired = account(Instant.parse("2020-01-01T00:00:00Z"));
        assertFalse(expired.hasFeature(Feature.MULTI_RESTAURANT), "PRO la tiene, TRIAL no");
        assertTrue(account(null).hasFeature(Feature.MULTI_RESTAURANT));
    }
}
