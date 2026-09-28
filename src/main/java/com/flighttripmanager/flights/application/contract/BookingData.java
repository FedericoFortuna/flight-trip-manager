package com.flighttripmanager.flights.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record BookingData(String bookingReference, String electronicTicketNumber, MoneyData pricePaid, MoneyData ticketTotal, String baggage, String seat) {
    @Override public String toString() { return "BookingData[REDACTED]"; }
}
