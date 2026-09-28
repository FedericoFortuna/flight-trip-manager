package com.flighttripmanager.flights.application.contract;
public record FlightChange<T>(boolean present, T value) {
    public static <T> FlightChange<T> absent() { return new FlightChange<>(false, null); }
    public static <T> FlightChange<T> of(T value) { return new FlightChange<>(true, value); }
    public T apply(T old) { return present ? value : old; }
}
