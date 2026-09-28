package com.flighttripmanager.flights.domain.model;
import java.util.UUID;
public record FlightPassenger(UUID passengerId, String seat, String baggage, String electronicTicketNumber, FlightMoney pricePaid) {
    public FlightPassenger {
        if (passengerId == null) throw new FlightRuleViolation("passengerId");
        seat = FlightValues.text(seat, 20, "seat");
        baggage = FlightValues.text(baggage, 500, "baggage");
        electronicTicketNumber = FlightValues.text(electronicTicketNumber, 40, "electronicTicketNumber");
    }
    @Override public String toString() { return "FlightPassenger[REDACTED]"; }
}
