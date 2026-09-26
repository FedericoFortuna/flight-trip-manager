package com.flighttripmanager.trips.application.contract;
public record PatchPassenger(Long version, Change<String> firstName, Change<String> lastName, Change<String> notes) {}
