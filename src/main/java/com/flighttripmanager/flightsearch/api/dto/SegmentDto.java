package com.flighttripmanager.flightsearch.api.dto;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record SegmentDto(String origin, String destination, String marketingAirline, String operatingAirline, String flightNumber, LocalDateTime departingLocal, LocalDateTime arrivingLocal, Instant departingAt, Instant arrivingAt, List<String> technicalStops) {}
