package com.flighttripmanager.flightsearch.application.port.in;
import java.util.UUID;
import com.flighttripmanager.flightsearch.application.contract.*;
public interface SavedOptions {
    SavedOptionView save(UUID tripId,UUID legId,SaveOption command);
    SavedOptionView get(UUID id);
    SearchPage<SavedOptionView> list(UUID tripId,UUID legId,SearchQuery page);
    PriceObservation observe(UUID id,SaveOption command);
    SearchPage<PriceSnapshotData> history(UUID id,SearchQuery page);
    void delete(UUID id,long version);
}
