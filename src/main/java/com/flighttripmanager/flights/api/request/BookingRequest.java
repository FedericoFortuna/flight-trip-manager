package com.flighttripmanager.flights.api.request;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
public record BookingRequest(String bookingReference, String electronicTicketNumber, MoneyRequest pricePaid, MoneyRequest ticketTotal, String baggage, String seat) {
    @Override public String toString() { return "BookingRequest[REDACTED]"; }
}
