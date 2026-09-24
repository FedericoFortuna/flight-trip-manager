package com.flighttripmanager.catalog.application.port.out;

import java.time.Instant;

public record ImportMetadata(String source, String externalId, Instant observedAt, Instant syncedAt) {}
