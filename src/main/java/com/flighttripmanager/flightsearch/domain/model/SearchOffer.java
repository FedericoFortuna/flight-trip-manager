package com.flighttripmanager.flightsearch.domain.model;
import java.math.*;
import java.time.*;
import java.util.*;
public record SearchOffer(String provider, String providerOfferId, String ownerAirline, boolean synthetic,
        boolean testMode, BigDecimal amount, String currency, BagAvailability checkedBag,
        Instant observedAt, Instant expiresAt, List<SearchSlice> slices) {
    public SearchOffer {
        if(provider==null||providerOfferId==null||ownerAirline==null||!ownerAirline.matches("[A-Z0-9]{2}"))
            throw new SearchRuleViolation("offer");
        if(amount==null||amount.signum()<0||amount.scale()>4||amount.precision()>19
                ||(long)amount.precision()-amount.scale()>19)throw new SearchRuleViolation("amount");
        try{Currency.getInstance(currency);}catch(IllegalArgumentException|NullPointerException e){throw new SearchRuleViolation("currency");}
        if(observedAt==null||expiresAt==null||!expiresAt.isAfter(observedAt)||checkedBag==null)throw new SearchRuleViolation("expiry");
        slices=List.copyOf(slices);
        if(slices.isEmpty()||slices.size()>2)throw new SearchRuleViolation("slices");
    }
    public BigDecimal amountUsd(){return "USD".equals(currency)?amount:null;}
    public long durationMinutes(){return slices.stream().mapToLong(SearchSlice::durationMinutes).sum();}
    public int stops(){return slices.stream().mapToInt(SearchSlice::stops).sum();}
    public List<String> missingCosts(){
        return checkedBag==BagAvailability.INCLUDED?List.of("SEAT_SELECTION","AIRPORT_TRANSFER"):
            List.of("CHECKED_BAGGAGE","SEAT_SELECTION","AIRPORT_TRANSFER");
    }
    public boolean matches(SearchCriteria criteria, SearchCriteria.Dates dates){
        if(slices.size()!=(criteria.returnDate()==null?1:2))return false;
        if(slices.size()==2){
            var outbound=slices.get(0).segments();
            if(slices.get(1).segments().get(0).departingAt().isBefore(outbound.get(outbound.size()-1).arrivingAt()))return false;
        }
        for(int i=0;i<slices.size();i++){
            var slice=slices.get(i);var first=slice.segments().get(0);var last=slice.segments().get(slice.segments().size()-1);
            if(!first.origin().equals(i==0?criteria.origin():criteria.destination())
                ||!last.destination().equals(i==0?criteria.destination():criteria.origin())
                ||!first.departingLocal().toLocalDate().equals(i==0?dates.departure():dates.returning()))return false;
            var f=criteria.filters();
            if(f.maxStops()!=null&&slice.stops()>f.maxStops()||f.maxDurationMinutes()!=null&&slice.durationMinutes()>f.maxDurationMinutes()
                    ||!f.acceptsTime(first.departingLocal().toLocalTime()))return false;
            for(var segment:slice.segments()){
                if(!f.airlines().isEmpty()&&!f.airlines().contains(segment.marketingAirline()))return false;
                if(f.excludedAirports().contains(segment.origin())||f.excludedAirports().contains(segment.destination())
                        ||segment.technicalStops().stream().anyMatch(f.excludedAirports()::contains))return false;
            }
        }
        return !criteria.filters().checkedBagRequired()||checkedBag==BagAvailability.INCLUDED;
    }
}
