package com.chefcontrol.domain.shared.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Central time utilities for Chef Control.
 * All calendar/date-time operations resolve against the configured zone (default: Argentina).
 * Persisted Instants are always absolute UTC — zone only affects local-date operations.
 */
public final class ChefControlTime {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    public static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss z");

    private ChefControlTime() {}

    public static ZonedDateTime zonedNow() {
        return ZonedDateTime.now(DEFAULT_ZONE);
    }

    public static Instant nowInstant() {
        return zonedNow().toInstant();
    }

    public static LocalDateTime nowDateTime() {
        return zonedNow().toLocalDateTime();
    }

    public static LocalDate nowDate() {
        return zonedNow().toLocalDate();
    }

    public static Instant startOfDay(LocalDate date) {
        return startOfDay(date, DEFAULT_ZONE);
    }

    public static Instant startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    public static Instant endOfDay(LocalDate date) {
        return endOfDay(date, DEFAULT_ZONE);
    }

    // Used for whatsapp_sessions.expires_at — session expires at restaurant's local midnight
    public static Instant endOfDay(LocalDate date, ZoneId zone) {
        return date.atTime(23, 59, 59).atZone(zone).toInstant();
    }

    public static LocalDate toLocalDate(Instant instant) {
        return toLocalDate(instant, DEFAULT_ZONE);
    }

    public static LocalDate toLocalDate(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate();
    }

    public static ZonedDateTime parseTimestamp(String timestamp) {
        try {
            return ZonedDateTime.parse(timestamp, FORMATTER);
        } catch (DateTimeParseException ex) {
            return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_ZONED_DATE_TIME);
        }
    }
}
