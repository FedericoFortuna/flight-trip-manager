package com.flighttripmanager.catalog.application.contract;

public record AirlineImport(String externalId, String iataCode, String icaoCode, String name, String country, Boolean active) {}
