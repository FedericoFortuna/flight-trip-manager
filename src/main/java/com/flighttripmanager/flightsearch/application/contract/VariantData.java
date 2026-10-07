package com.flighttripmanager.flightsearch.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;

public record VariantData(int offset,LocalDate departureDate,LocalDate returnDate,String status,int offers,List<String> warnings) {}
