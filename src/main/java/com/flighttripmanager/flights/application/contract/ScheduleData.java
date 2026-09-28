package com.flighttripmanager.flights.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record ScheduleData(Instant scheduledDeparture, Instant scheduledArrival, Instant estimatedDeparture, Instant estimatedArrival, Instant actualDeparture, Instant actualArrival) {}
