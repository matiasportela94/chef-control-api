package com.chefcontrol.infrastructure.time;

import com.chefcontrol.domain.shared.time.TimeProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * TimeProvider with a fixed instant — use in tests to get deterministic time.
 *
 * TimeProvider time = new FixedTimeProvider("2026-01-15T10:00:00Z");
 */
public class FixedTimeProvider implements TimeProvider {

    private final Instant fixedInstant;
    private final ZoneId zone;

    public FixedTimeProvider(Instant fixedInstant) {
        this.fixedInstant = fixedInstant;
        this.zone = TimeProvider.super.zoneId();
    }

    public FixedTimeProvider(String isoInstant) {
        this(Instant.parse(isoInstant));
    }

    public FixedTimeProvider(Instant fixedInstant, ZoneId zone) {
        this.fixedInstant = fixedInstant;
        this.zone = zone;
    }

    @Override
    public Instant now() {
        return fixedInstant;
    }

    @Override
    public LocalDateTime nowDateTime() {
        return LocalDateTime.ofInstant(fixedInstant, zone);
    }

    @Override
    public LocalDate nowDate() {
        return fixedInstant.atZone(zone).toLocalDate();
    }

    @Override
    public ZoneId zoneId() {
        return zone;
    }
}
