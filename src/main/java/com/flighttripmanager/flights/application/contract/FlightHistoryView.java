package com.flighttripmanager.flights.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record FlightHistoryView(UUID id, UUID flightId, long revision, String field, String previousValue, String value, String source, Instant recordedAt) {}
