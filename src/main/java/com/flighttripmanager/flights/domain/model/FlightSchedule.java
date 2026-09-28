package com.flighttripmanager.flights.domain.model;
import java.time.Instant;
public record FlightSchedule(Instant scheduledDeparture, Instant scheduledArrival, Instant estimatedDeparture,
        Instant estimatedArrival, Instant actualDeparture, Instant actualArrival) {
    public FlightSchedule {
        FlightValues.pair(scheduledDeparture, scheduledArrival);
        FlightValues.pair(estimatedDeparture, estimatedArrival);
        FlightValues.pair(actualDeparture, actualArrival);
    }
}
