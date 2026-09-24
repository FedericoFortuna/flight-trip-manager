package com.flighttripmanager.catalog.application.contract;

import java.util.List;

public record CatalogImportResult(int created, int updated, int unchanged, List<ImportItemResult> items) {
    public CatalogImportResult { items = List.copyOf(items); }
    public static CatalogImportResult from(List<ImportItemResult> items) {
        return new CatalogImportResult(count(items, ImportStatus.CREATED), count(items, ImportStatus.UPDATED),
                count(items, ImportStatus.UNCHANGED), items);
    }
    private static int count(List<ImportItemResult> items, ImportStatus status) {
        return (int) items.stream().filter(item -> item.status() == status).count();
    }
}
