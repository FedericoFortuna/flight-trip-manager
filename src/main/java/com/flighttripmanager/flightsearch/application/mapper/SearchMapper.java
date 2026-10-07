package com.flighttripmanager.flightsearch.application.mapper;
import org.mapstruct.*;
import com.flighttripmanager.flightsearch.domain.model.*;
import com.flighttripmanager.flightsearch.application.contract.*;
@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface SearchMapper {
    SearchCriteria domain(CriteriaData data);
    CriteriaData data(SearchCriteria criteria);
    RankedData data(SearchRanking.Ranked ranked);
    @Mapping(target="amountUsd",expression="java(offer.amountUsd())")
    @Mapping(target="missingCosts",expression="java(offer.missingCosts())")
    OfferData data(SearchOffer offer);
    @Mapping(target="durationMinutes",expression="java(slice.durationMinutes())")
    @Mapping(target="stops",expression="java(slice.stops())")
    SliceData data(SearchSlice slice);
}
