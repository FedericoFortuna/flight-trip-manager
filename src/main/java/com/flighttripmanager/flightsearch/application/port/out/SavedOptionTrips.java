package com.flighttripmanager.flightsearch.application.port.out;
import java.time.Instant;
import java.util.UUID;
public interface SavedOptionTrips {
    record Context(long version,Instant updatedAt,boolean acceptingAlternatives){}
    Context inspect(UUID tripId,UUID legId,boolean lock);
    void validateRoute(UUID tripId,UUID legId,String origin,String destination);
    long advance(UUID tripId,long version);
}
