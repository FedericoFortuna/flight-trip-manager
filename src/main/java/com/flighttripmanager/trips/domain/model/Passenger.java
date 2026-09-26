package com.flighttripmanager.trips.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Passenger(UUID id, UUID tripId, String firstName, String lastName, String notes,
        Instant createdAt, Instant updatedAt) {
    public Passenger {
        if (id == null || tripId == null) throw new TripRuleViolation("passenger");
        firstName = name(firstName, "firstName");
        lastName = name(lastName, "lastName");
        if (notes != null && (notes.length() > 2000 || notes.codePoints().anyMatch(
                c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))) {
            throw new TripRuleViolation("notes");
        }
        if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
            throw new TripRuleViolation("timestamps");
        }
    }
    private static String name(String value, String field) {
        if (value == null) throw new TripRuleViolation(field);
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > 100
                || normalized.codePoints().anyMatch(Character::isISOControl)) throw new TripRuleViolation(field);
        return normalized;
    }
}
