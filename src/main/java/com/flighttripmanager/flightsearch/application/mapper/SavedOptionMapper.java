package com.flighttripmanager.flightsearch.application.mapper;
import org.mapstruct.*;
import com.flighttripmanager.flightsearch.domain.model.*;
import com.flighttripmanager.flightsearch.application.contract.*;
@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface SavedOptionMapper {
    @Mapping(target="tripVersion",source="version")
    @Mapping(target="criteria",expression="java(searches.data(option.criteria()))")
    @Mapping(target="initialOffer",expression="java(searches.data(option.initialOffer()))")
    SavedOptionView view(SavedFlightOption option,long version,@Context SearchMapper searches);
    PriceSnapshotData data(FlightPriceSnapshot price);
}
