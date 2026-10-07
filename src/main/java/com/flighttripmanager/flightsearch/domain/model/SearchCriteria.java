package com.flighttripmanager.flightsearch.domain.model;
import java.time.*;
import java.util.*;
public record SearchCriteria(String origin, String destination, LocalDate departureDate, LocalDate returnDate,
        int flexDays, int adults, List<Integer> childAges, Cabin cabin, SearchFilters filters) {
    public SearchCriteria {
        origin=airport(origin); destination=airport(destination);
        if(origin.equals(destination))throw new SearchRuleViolation("destination");
        if(departureDate==null||departureDate.getYear()<1||departureDate.getYear()>9998)throw new SearchRuleViolation("departureDate");
        if(returnDate!=null&&(returnDate.isBefore(departureDate)||returnDate.getYear()>9998))throw new SearchRuleViolation("returnDate");
        if(flexDays<0||flexDays>3)throw new SearchRuleViolation("flexDays");
        if(childAges!=null&&childAges.stream().anyMatch(Objects::isNull))throw new SearchRuleViolation("childAges");
        childAges=childAges==null?List.of():List.copyOf(childAges);
        if(adults<1||adults>9||adults+childAges.size()>9||childAges.stream().anyMatch(a->a<0||a>17)
                ||childAges.stream().filter(a->a<2).count()>adults)throw new SearchRuleViolation("passengers");
        childAges=childAges.stream().sorted().toList();
        if(cabin==null)cabin=Cabin.ECONOMY;
        if(filters==null)filters=new SearchFilters(null,null,null,null,null,false,null);
    }
    public record Dates(int offset, LocalDate departure, LocalDate returning) {}
    public List<Dates> variants(){
        List<Dates> result=new ArrayList<>();
        // Selected date first, then nearest alternatives, for useful results under a quota/deadline.
        result.add(new Dates(0,departureDate,returnDate));
        for(int d=1;d<=flexDays;d++)for(int offset:new int[]{-d,d})
            result.add(new Dates(offset,departureDate.plusDays(offset),returnDate==null?null:returnDate.plusDays(offset)));
        return List.copyOf(result);
    }
    private static String airport(String input){
        if(input==null||!input.strip().toUpperCase(Locale.ROOT).matches("[A-Z]{3}"))throw new SearchRuleViolation("airport");
        return input.strip().toUpperCase(Locale.ROOT);
    }
}
