package com.flighttripmanager.flights.api.response;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
public record BookingResponse(String bookingReference, String electronicTicketNumber, MoneyResponse pricePaid, MoneyResponse ticketTotal, String baggage, String seat) {
    @Override public String toString() { return "BookingResponse[REDACTED]"; }
}
