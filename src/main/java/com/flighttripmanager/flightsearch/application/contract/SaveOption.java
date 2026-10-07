package com.flighttripmanager.flightsearch.application.contract;
import java.util.UUID;
public record SaveOption(Long version, UUID searchId, String provider, String providerOfferId) {}
