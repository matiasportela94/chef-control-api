package com.chefcontrol.application.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** El mes del dashboard es el de Argentina (UTC−3), no el de UTC. */
class DashboardMonthTest {

    @Test
    void aSaleOnTheFirstAt1AmArgentinaIsThisMonthAndOneTheNightBeforeIsLastMonth() {
        Instant now = Instant.parse("2026-12-01T04:00:00Z");          // 1/12 01:00 en Argentina
        Instant saleOnTheFirst = Instant.parse("2026-12-01T04:00:00Z");
        Instant saleTheNightBefore = Instant.parse("2026-12-01T01:00:00Z"); // 30/11 22:00 en Argentina: en UTC ya es diciembre

        Instant monthStart = DashboardService.monthStart(now, 0);
        Instant prevMonthStart = DashboardService.monthStart(now, 1);

        assertEquals(Instant.parse("2026-12-01T03:00:00Z"), monthStart);
        assertEquals(Instant.parse("2026-11-01T03:00:00Z"), prevMonthStart);
        assertFalse(saleOnTheFirst.isBefore(monthStart));
        assertTrue(saleTheNightBefore.isBefore(monthStart));
        assertFalse(saleTheNightBefore.isBefore(prevMonthStart));
    }

    @Test
    void theLastNightOfTheMonthIsStillThatMonthAndJanuaryGoesBackToDecember() {
        // 31/12 22:00 en Argentina = 1/1 01:00 UTC: el mes sigue siendo diciembre
        assertEquals(Instant.parse("2026-12-01T03:00:00Z"), DashboardService.monthStart(Instant.parse("2027-01-01T01:00:00Z"), 0));
        assertEquals(Instant.parse("2026-12-01T03:00:00Z"), DashboardService.monthStart(Instant.parse("2027-01-15T12:00:00Z"), 1));
    }
}
