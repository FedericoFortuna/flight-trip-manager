package com.flighttripmanager.trips.api.mapper;
import org.mapstruct.*;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.api.request.CreatePassengerRequest;
import com.flighttripmanager.trips.api.response.*;
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PassengerApiMapper {
    CreatePassenger command(CreatePassengerRequest request);
    PassengerResponse response(PassengerView view);
    default TripsPageResponse<PassengerResponse> page(TripsPage<PassengerView> page) {
        return new TripsPageResponse<>(page.items().stream().map(this::response).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
