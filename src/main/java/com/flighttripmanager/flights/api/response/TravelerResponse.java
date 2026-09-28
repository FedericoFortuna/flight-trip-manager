package com.flighttripmanager.flights.api.response;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
public record TravelerResponse(UUID passengerId, String seat, String baggage, String electronicTicketNumber, MoneyResponse pricePaid) {
    @Override public String toString() { return "TravelerResponse[REDACTED]"; }
}
