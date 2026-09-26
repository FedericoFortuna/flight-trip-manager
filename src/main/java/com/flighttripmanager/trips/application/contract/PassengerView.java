package com.flighttripmanager.trips.application.contract;
import java.time.Instant;
import java.util.UUID;
public record PassengerView(UUID id, UUID tripId, String firstName, String lastName, String notes,
        long tripVersion, Instant createdAt, Instant updatedAt) {}
