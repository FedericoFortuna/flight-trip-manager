package com.flighttripmanager.catalog.application.contract;

import java.util.UUID;

public record LocationView(UUID id, LocationKind type, String name, String city, String country) {}

