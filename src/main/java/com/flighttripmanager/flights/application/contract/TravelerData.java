package com.flighttripmanager.flights.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record TravelerData(UUID passengerId, String seat, String baggage, String electronicTicketNumber, MoneyData pricePaid) {
    @Override public String toString() { return "TravelerData[REDACTED]"; }
}
