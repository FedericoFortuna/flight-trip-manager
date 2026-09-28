package com.flighttripmanager.flights.application.contract;
import java.util.UUID;
public record CreateFlight(Long version, FlightData data, UUID idempotencyKey) {}
