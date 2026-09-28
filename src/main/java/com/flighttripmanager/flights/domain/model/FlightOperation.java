package com.flighttripmanager.flights.domain.model;
public record FlightOperation(FlightStatus status, String departureTerminal, String departureGate,
        String arrivalTerminal, String arrivalGate) {
    public FlightOperation {
        if (status == null) status = FlightStatus.SCHEDULED;
        departureTerminal = FlightValues.text(departureTerminal, 40, "departureTerminal");
        departureGate = FlightValues.text(departureGate, 40, "departureGate");
        arrivalTerminal = FlightValues.text(arrivalTerminal, 40, "arrivalTerminal");
        arrivalGate = FlightValues.text(arrivalGate, 40, "arrivalGate");
    }
}
