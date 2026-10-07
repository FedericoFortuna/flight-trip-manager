package com.flighttripmanager.flightsearch.domain.model;
import java.time.Duration;
import java.util.*;
public record SearchSlice(List<SearchSegment> segments) {
    public SearchSlice {
        segments=List.copyOf(segments);
        if(segments.isEmpty()||segments.size()>8)throw new SearchRuleViolation("segments");
        for(int i=1;i<segments.size();i++){
            var before=segments.get(i-1);var next=segments.get(i);
            if(!before.destination().equals(next.origin())||next.departingAt().isBefore(before.arrivingAt()))
                throw new SearchRuleViolation("connection");
        }
    }
    public long durationMinutes(){return Duration.between(segments.get(0).departingAt(),segments.get(segments.size()-1).arrivingAt()).toMinutes();}
    public int stops(){return segments.size()-1+segments.stream().mapToInt(s->s.technicalStops().size()).sum();}
}
