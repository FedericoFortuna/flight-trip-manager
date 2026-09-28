package com.flighttripmanager.flights.api.mapper;
import java.util.*;
import org.mapstruct.*;
import com.flighttripmanager.flights.application.contract.*;
import com.flighttripmanager.flights.api.request.*;
import com.flighttripmanager.flights.api.response.*;
@Mapper(componentModel="spring", unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface FlightApiMapper {
    CreateFlight command(CreateFlightRequest request, UUID idempotencyKey);
    FlightData data(FlightRequest request);
    ScheduleData data(ScheduleRequest request);
    OperationData data(OperationRequest request);
    BookingData data(BookingRequest request);
    List<TravelerData> passengers(List<TravelerRequest> request);
    FlightViewResponse response(FlightView flight);
    HistoryResponse response(FlightHistoryView history);
    default FlightPageResponse<FlightViewResponse> flights(FlightPage<FlightView> page) {
        return new FlightPageResponse<>(page.items().stream().map(this::response).toList(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
    default FlightPageResponse<HistoryResponse> history(FlightPage<FlightHistoryView> page) {
        return new FlightPageResponse<>(page.items().stream().map(this::response).toList(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
