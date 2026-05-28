package com.chefcontrol.domain.shared.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Time provider abstraction.
 * Inject this interface in use cases instead of calling Instant.now() or LocalDate.now() directly.
 * Enables deterministic tests without mocking static methods.
 */
public interface TimeProvider {

    Instant now();

    LocalDateTime nowDateTime();

    LocalDate nowDate();

    default ZoneId zoneId() {
        return ChefControlTime.DEFAULT_ZONE;
    }

    default ZonedDateTime zonedNow() {
        return now().atZone(zoneId());
    }

    default Instant startOfDay(LocalDate date) {
        return ChefControlTime.startOfDay(date, zoneId());
    }

    // Session expiry: midnight of the current operational day
    default Instant endOfToday() {
        return ChefControlTime.endOfDay(nowDate(), zoneId());
    }

    default Instant endOfDay(LocalDate date) {
        return ChefControlTime.endOfDay(date, zoneId());
    }

    default LocalDate toLocalDate(Instant instant) {
        return ChefControlTime.toLocalDate(instant, zoneId());
    }
}
