package com.flighttripmanager.flightsearch.api.dto;
import java.time.LocalDate;import java.util.List;
public record VariantDto(int offset,LocalDate departureDate,LocalDate returnDate,String status,int offers,List<String> warnings) {}
