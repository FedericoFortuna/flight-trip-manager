package com.flighttripmanager.trips.domain.model;

import java.time.*;
import java.util.UUID;

public record TripLeg(UUID id, UUID tripId, Place origin, Place destination, TransportType transportType,
        LocalDate departureDate, Instant departureDateTime, LocalDate arrivalDate, Instant arrivalDateTime,
        LegStatus declaredStatus, Integer manualOrder, Instant createdAt, Instant updatedAt) {
    public TripLeg {
        if (id == null || tripId == null || origin == null || destination == null
                || transportType == null || declaredStatus == null || createdAt == null || updatedAt == null) {
            throw new TripRuleViolation("leg");
        }
        if (origin.equals(destination)) throw new TripRuleViolation("destination");
        departureDate = date(departureDate, departureDateTime, "departureDate");
        arrivalDate = date(arrivalDate, arrivalDateTime, "arrivalDate");
        if (departureDate == null && (declaredStatus != LegStatus.PLANNED || arrivalDate != null)) {
            throw new TripRuleViolation("departureDate");
        }
        if (arrivalDate != null && arrivalDate.isBefore(departureDate)) throw new TripRuleViolation("arrivalDate");
        if (departureDateTime != null && arrivalDateTime != null && arrivalDateTime.isBefore(departureDateTime)) {
            throw new TripRuleViolation("arrivalDateTime");
        }
        if (manualOrder != null && manualOrder < 0) throw new TripRuleViolation("manualOrder");
    }
    private static LocalDate date(LocalDate date, Instant instant, String field) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) throw new TripRuleViolation(field);
        if (instant == null) return date;
        LocalDate utc;
        try { utc = instant.atOffset(ZoneOffset.UTC).toLocalDate(); }
        catch (DateTimeException error) { throw new TripRuleViolation(field); }
        if (utc.getYear() < 1 || utc.getYear() > 9999 || instant.getNano() % 1000 != 0
                || (date != null && !date.equals(utc))) throw new TripRuleViolation(field);
        return utc;
    }
    public LegStatus status(Instant now) {
        if (declaredStatus != LegStatus.BOOKED && declaredStatus != LegStatus.UPCOMING) return declaredStatus;
        LocalDate today = now.atOffset(ZoneOffset.UTC).toLocalDate();
        boolean future = departureDateTime == null ? departureDate.isAfter(today) : departureDateTime.isAfter(now);
        if (future) return LegStatus.UPCOMING;
        boolean finished = arrivalDateTime != null ? !arrivalDateTime.isAfter(now)
                : arrivalDate != null && arrivalDate.isBefore(today);
        return finished ? LegStatus.COMPLETED : LegStatus.IN_PROGRESS;
    }
    public TripLeg ordered(Integer order, Instant now) {
        return new TripLeg(id, tripId, origin, destination, transportType, departureDate, departureDateTime,
                arrivalDate, arrivalDateTime, declaredStatus, order, createdAt, now);
    }
}
