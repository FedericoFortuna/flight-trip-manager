package com.flighttripmanager.trips.application.mapper;
import org.mapstruct.*;
import com.flighttripmanager.trips.application.contract.PassengerView;
import com.flighttripmanager.trips.domain.model.Passenger;
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PassengerMapper {
    PassengerView view(Passenger passenger, long tripVersion);
}
