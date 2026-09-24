package com.flighttripmanager.catalog.application.port.out;

import java.time.Instant;

public record StoredCatalogEntry<T>(T value, Instant observedAt) {}
