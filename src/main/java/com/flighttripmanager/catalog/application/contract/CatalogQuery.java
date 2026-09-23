package com.flighttripmanager.catalog.application.contract;

import java.util.Locale;

/** Bounded pagination and literal, case-insensitive substring search. */
public record CatalogQuery(String q, int page, int size) {
    public CatalogQuery {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100 || (q != null && q.length() > 100)) {
            throw new IllegalArgumentException("Invalid catalog query");
        }
        q = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
    }
}

