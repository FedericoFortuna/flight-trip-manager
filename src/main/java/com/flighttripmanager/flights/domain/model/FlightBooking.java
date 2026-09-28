package com.flighttripmanager.flights.domain.model;
public record FlightBooking(String bookingReference, String electronicTicketNumber, FlightMoney pricePaid,
        FlightMoney ticketTotal, String baggage, String seat) {
    public FlightBooking {
        bookingReference = FlightValues.code(bookingReference, 32, "bookingReference");
        electronicTicketNumber = FlightValues.text(electronicTicketNumber, 40, "electronicTicketNumber");
        baggage = FlightValues.text(baggage, 500, "baggage");
        seat = FlightValues.text(seat, 20, "seat");
    }
    @Override public String toString() { return "FlightBooking[REDACTED]"; }
}
