package com.flighttripmanager.catalog.api.response;

import java.util.List;
import java.util.UUID;
import com.flighttripmanager.catalog.application.contract.CatalogKind;
import com.flighttripmanager.catalog.application.contract.ImportStatus;

public record CatalogImportResponse(int created, int updated, int unchanged, List<Item> items) {
    public CatalogImportResponse { items = List.copyOf(items); }
    public record Item(CatalogKind kind, String externalId, UUID id, ImportStatus status) {}
}
