package com.flighttripmanager.flightsearch.domain.model;
import java.time.*;
import java.util.*;
public record SearchSegment(String origin, String destination, String marketingAirline, String operatingAirline,
        String flightNumber, LocalDateTime departingLocal, LocalDateTime arrivingLocal,
        Instant departingAt, Instant arrivingAt, List<String> technicalStops) {
    public SearchSegment {
        if(origin==null||!origin.matches("[A-Z]{3}")||destination==null||!destination.matches("[A-Z]{3}")
            ||marketingAirline==null||!marketingAirline.matches("[A-Z0-9]{2}")
            ||departingLocal==null||arrivingLocal==null||departingAt==null||arrivingAt==null
            ||!arrivingAt.isAfter(departingAt))throw new SearchRuleViolation("segment");
        technicalStops=List.copyOf(technicalStops);
        if(technicalStops.stream().anyMatch(code->!code.matches("[A-Z]{3}")))throw new SearchRuleViolation("technicalStops");
    }
}
