package com.flighttripmanager.trips.application.contract;

/** Distinguishes an omitted patch property from explicit null. */
public record Change<T>(boolean present, T value) {
    public static <T> Change<T> absent() { return new Change<>(false, null); }
    public static <T> Change<T> of(T value) { return new Change<>(true, value); }
    public T apply(T previous) { return present ? value : previous; }
}
