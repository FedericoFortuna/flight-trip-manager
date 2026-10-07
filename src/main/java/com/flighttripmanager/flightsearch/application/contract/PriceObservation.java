package com.flighttripmanager.flightsearch.application.contract;
public record PriceObservation(PriceSnapshotData snapshot,long tripVersion,boolean replayed) {}
