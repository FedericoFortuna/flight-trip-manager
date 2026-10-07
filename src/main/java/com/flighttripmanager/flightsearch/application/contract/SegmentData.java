package com.flighttripmanager.flightsearch.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record SegmentData(String origin, String destination, String marketingAirline, String operatingAirline, String flightNumber, LocalDateTime departingLocal, LocalDateTime arrivingLocal, Instant departingAt, Instant arrivingAt, List<String> technicalStops) {}
