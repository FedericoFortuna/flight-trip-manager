package com.flighttripmanager.flights.api.request;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
public record TravelerRequest(UUID passengerId, String seat, String baggage, String electronicTicketNumber, MoneyRequest pricePaid) {
    @Override public String toString() { return "TravelerRequest[REDACTED]"; }
}
