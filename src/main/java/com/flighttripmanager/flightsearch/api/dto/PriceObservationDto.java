package com.flighttripmanager.flightsearch.api.dto;
public record PriceObservationDto(PriceSnapshotDto snapshot,long tripVersion,boolean replayed) {}
