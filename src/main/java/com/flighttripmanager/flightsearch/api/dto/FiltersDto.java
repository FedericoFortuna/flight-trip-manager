package com.flighttripmanager.flightsearch.api.dto;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record FiltersDto(Set<String> airlines, Integer maxStops, LocalTime departureFrom, LocalTime departureTo, Integer maxDurationMinutes, boolean checkedBagRequired, Set<String> excludedAirports) {}
