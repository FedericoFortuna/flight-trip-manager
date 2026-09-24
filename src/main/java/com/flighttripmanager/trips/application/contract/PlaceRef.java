package com.flighttripmanager.trips.application.contract;

import java.util.UUID;
public record PlaceRef(PlaceTypeValue kind, UUID id) {}
