package com.flighttripmanager.catalog.domain.model;

import java.util.Objects;

final class CatalogValues {
    private CatalogValues() {}
    static String text(String value, int max) {
        Objects.requireNonNull(value);
        if (value.isBlank() || !value.equals(value.strip()) || value.length() > max) {
            throw new IllegalArgumentException("Invalid catalog text");
        }
        return value;
    }
    static String code(String value, String pattern) {
        if (value == null || !value.matches(pattern)) throw new IllegalArgumentException("Invalid catalog code");
        return value;
    }
    static String optionalCode(String value, String pattern) {
        return value == null ? null : code(value, pattern);
    }
}

