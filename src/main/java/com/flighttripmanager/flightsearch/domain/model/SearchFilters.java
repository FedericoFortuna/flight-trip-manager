package com.flighttripmanager.flightsearch.domain.model;
import java.time.LocalTime;
import java.util.*;
public record SearchFilters(Set<String> airlines, Integer maxStops, LocalTime departureFrom, LocalTime departureTo,
        Integer maxDurationMinutes, boolean checkedBagRequired, Set<String> excludedAirports) {
    public SearchFilters {
        airlines=codes(airlines,"[A-Z0-9]{2}","airlines");
        excludedAirports=codes(excludedAirports,"[A-Z]{3}","excludedAirports");
        if(maxStops!=null&&(maxStops<0||maxStops>3))throw new SearchRuleViolation("maxStops");
        if(maxDurationMinutes!=null&&(maxDurationMinutes<1||maxDurationMinutes>10080))throw new SearchRuleViolation("maxDurationMinutes");
        if((departureFrom==null)!=(departureTo==null))throw new SearchRuleViolation("departureTime");
    }
    private static Set<String> codes(Set<String> input,String pattern,String field){
        if(input==null)return Set.of();
        if(input.size()>20)throw new SearchRuleViolation(field);
        Set<String> result=new TreeSet<>();
        for(String code:input){
            if(code==null||!code.strip().toUpperCase(Locale.ROOT).matches(pattern))throw new SearchRuleViolation(field);
            result.add(code.strip().toUpperCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(result);
    }
    public boolean acceptsTime(LocalTime time){
        if(departureFrom==null)return true;
        return departureFrom.compareTo(departureTo)<=0
            ? !time.isBefore(departureFrom)&&!time.isAfter(departureTo)
            : !time.isBefore(departureFrom)||!time.isAfter(departureTo);
    }
}
