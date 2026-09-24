package com.flighttripmanager.trips.api.mapper;

import org.mapstruct.*;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.api.request.*;
import com.flighttripmanager.trips.api.response.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TripApiMapper {
    CreateTrip command(CreateTripRequest request);
    CreateLeg command(CreateLegRequest request);
    ReorderLegs command(ReorderLegsRequest request);
    TripResponse response(TripView view);
    LegResponse response(LegView view);
    default TripsPageResponse<TripResponse> trips(TripsPage<TripView> page) {
        return new TripsPageResponse<>(page.items().stream().map(this::response).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
    default TripsPageResponse<LegResponse> legs(TripsPage<LegView> page) {
        return new TripsPageResponse<>(page.items().stream().map(this::response).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
