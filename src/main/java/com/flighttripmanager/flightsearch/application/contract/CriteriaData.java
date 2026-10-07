package com.flighttripmanager.flightsearch.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record CriteriaData(String origin, String destination, LocalDate departureDate, LocalDate returnDate, int flexDays, int adults, List<Integer> childAges, String cabin, FiltersData filters) {}
