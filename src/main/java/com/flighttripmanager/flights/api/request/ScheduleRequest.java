package com.flighttripmanager.flights.api.request;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
public record ScheduleRequest(Instant scheduledDeparture, Instant scheduledArrival, Instant estimatedDeparture, Instant estimatedArrival, Instant actualDeparture, Instant actualArrival) {}
