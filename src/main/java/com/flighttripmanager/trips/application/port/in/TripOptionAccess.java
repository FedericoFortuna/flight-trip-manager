package com.flighttripmanager.trips.application.port.in;
import java.util.UUID;
import com.flighttripmanager.trips.application.contract.OptionTripContext;
public interface TripOptionAccess {
    OptionTripContext inspectOptions(UUID tripId, UUID legId, boolean lock);
}
