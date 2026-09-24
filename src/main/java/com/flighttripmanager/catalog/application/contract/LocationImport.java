package com.flighttripmanager.catalog.application.contract;

public record LocationImport(String externalId, LocationKind type, String name, String city, String country) {}
