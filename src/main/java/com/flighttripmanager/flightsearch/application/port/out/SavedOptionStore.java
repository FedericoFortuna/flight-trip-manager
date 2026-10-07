package com.flighttripmanager.flightsearch.application.port.out;
import java.time.Instant;
import java.util.*;
import com.flighttripmanager.flightsearch.domain.model.*;
import com.flighttripmanager.flightsearch.application.contract.*;
public interface SavedOptionStore {
    record Reference(UUID tripId,UUID legId){}
    Optional<Reference> reference(UUID id);
    Optional<SavedFlightOption> find(UUID id);
    List<SavedFlightOption> list(UUID tripId,UUID legId);
    void insert(SavedFlightOption option,FlightPriceSnapshot price);
    void append(FlightPriceSnapshot price);
    Optional<FlightPriceSnapshot> observation(UUID optionId,UUID searchId,String providerOfferId);
    SearchPage<FlightPriceSnapshot> history(UUID optionId,SearchQuery page);
    void delete(UUID id);
    void close(UUID tripId,UUID legId,String reason,Instant at);
}
