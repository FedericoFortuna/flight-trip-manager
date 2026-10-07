package com.flighttripmanager.flightsearch.infrastructure.persistence.mapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.flighttripmanager.flightsearch.domain.model.*;
import com.flighttripmanager.flightsearch.application.contract.*;
import com.flighttripmanager.flightsearch.application.mapper.SearchMapper;
import com.flighttripmanager.flightsearch.infrastructure.persistence.entity.*;
@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public abstract class SavedOptionPersistenceMapper {
    @Autowired protected ObjectMapper json;
    @Autowired protected SearchMapper searches;
    @Mapping(target="transportType",constant="FLIGHT")
    public abstract SavedOptionJpaEntity entity(SavedFlightOption option);
    public abstract SavedFlightOption domain(SavedOptionJpaEntity entity);
    public abstract FlightPriceJpaEntity entity(FlightPriceSnapshot price);
    public abstract FlightPriceSnapshot domain(FlightPriceJpaEntity entity);
    protected String write(SearchCriteria criteria){return serialize(searches.data(criteria));}
    protected String write(SearchOffer offer){return serialize(searches.data(offer));}
    protected SearchCriteria criteria(String value){return searches.domain(deserialize(value,CriteriaData.class));}
    protected SearchOffer offer(String value){return searches.domain(deserialize(value,OfferData.class));}
    private String serialize(Object value){
        try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Option serialization failed");}
    }
    private <T>T deserialize(String value,Class<T> type){
        try{return json.readValue(value,type);}catch(JsonProcessingException e){throw new IllegalStateException("Option deserialization failed");}
    }
}
