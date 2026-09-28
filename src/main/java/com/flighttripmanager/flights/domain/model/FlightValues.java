package com.flighttripmanager.flights.domain.model;
import java.time.*;
import java.util.Locale;
final class FlightValues {
    private FlightValues() {}
    static String text(String value, int max, String field) {
        if (value == null) return null;
        String result = value.strip();
        if (result.isEmpty() || result.length() > max || result.codePoints().anyMatch(Character::isISOControl)) {
            throw new FlightRuleViolation(field);
        }
        return result;
    }
    static String code(String value, int max, String field) {
        String normalized = text(value, max, field);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
    static void instant(Instant value) {
        if (value == null) return;
        try {
            int year = value.atOffset(ZoneOffset.UTC).getYear();
            if (year < 1 || year > 9999 || value.getNano() % 1000 != 0) throw new FlightRuleViolation("schedule");
        } catch (DateTimeException error) { throw new FlightRuleViolation("schedule"); }
    }
    static void pair(Instant departure, Instant arrival) {
        instant(departure); instant(arrival);
        if (departure != null && arrival != null && arrival.isBefore(departure)) throw new FlightRuleViolation("schedule");
    }
}
