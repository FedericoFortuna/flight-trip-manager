package com.flighttripmanager.catalog.application.contract;

import java.util.UUID;

public record ImportItemResult(CatalogKind kind, String externalId, UUID id, ImportStatus status) {}
