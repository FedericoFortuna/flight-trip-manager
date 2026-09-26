package com.flighttripmanager.trips.api.response;
import java.time.Instant;
import java.util.UUID;
public record PassengerResponse(UUID id, UUID tripId, String firstName, String lastName, String notes,
        long tripVersion, Instant createdAt, Instant updatedAt) {}
