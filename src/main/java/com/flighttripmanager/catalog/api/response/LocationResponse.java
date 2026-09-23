package com.flighttripmanager.catalog.api.response;

import java.util.UUID;
import com.flighttripmanager.catalog.application.contract.LocationKind;

public record LocationResponse(UUID id, LocationKind type, String name, String city, String country) {}

