package com.flighttripmanager.catalog.application.contract;

import java.math.BigDecimal;

public record AirportImport(String externalId, String iataCode, String icaoCode, String name, String city, String country,
        BigDecimal latitude, BigDecimal longitude, String timezone, Boolean active) {}
