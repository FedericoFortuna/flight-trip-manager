package com.flighttripmanager.flightsearch.domain.model;
import java.math.*;
import java.util.*;
public final class SearchRanking {
    public record Weights(BigDecimal price, BigDecimal duration, BigDecimal stops, BigDecimal schedule, BigDecimal baggage, BigDecimal missingCosts){
        public Weights {
            for(BigDecimal weight:List.of(price,duration,stops,schedule,baggage,missingCosts))
                if(weight.signum()<0||weight.compareTo(new BigDecimal("1000"))>0)throw new SearchRuleViolation("rankingWeights");
            if(price.add(duration).add(stops).add(schedule).add(baggage).add(missingCosts).signum()==0)throw new SearchRuleViolation("rankingWeights");
        }
    }
    public record Ranked(SearchOffer offer, BigDecimal score, String rankingGroup, Map<String,BigDecimal> penalties) {}
    private final Weights weights;
    public SearchRanking(Weights weights){this.weights=weights;}
    public List<Ranked> rank(List<SearchOffer> offers){
        List<Ranked> ranked=new ArrayList<>();
        record Range(BigDecimal min,BigDecimal max,long minDuration,long maxDuration){}
        Map<String,Range> ranges=new HashMap<>();
        for(var offer:offers)ranges.compute(offer.currency(),(currency,old)->old==null?
            new Range(offer.amount(),offer.amount(),offer.durationMinutes(),offer.durationMinutes()):
            new Range(old.min().min(offer.amount()),old.max().max(offer.amount()),
                Math.min(old.minDuration(),offer.durationMinutes()),Math.max(old.maxDuration(),offer.durationMinutes())));
        for(var offer:offers){
            var range=ranges.get(offer.currency());
            BigDecimal min=range.min(),max=range.max();
            long minDuration=range.minDuration(),maxDuration=range.maxDuration();
            Map<String,BigDecimal> parts=new LinkedHashMap<>();
            parts.put("price",ratio(offer.amount().subtract(min),max.subtract(min)).multiply(weights.price()));
            parts.put("duration",ratio(BigDecimal.valueOf(offer.durationMinutes()-minDuration),BigDecimal.valueOf(maxDuration-minDuration)).multiply(weights.duration()));
            parts.put("stops",BigDecimal.valueOf(offer.stops()).multiply(weights.stops()));
            long early=offer.slices().stream().filter(s->{int h=s.segments().get(0).departingLocal().getHour();return h<6||h>=22;}).count();
            parts.put("schedule",BigDecimal.valueOf(early).multiply(weights.schedule()));
            parts.put("baggage",offer.checkedBag()==BagAvailability.INCLUDED?BigDecimal.ZERO:weights.baggage());
            parts.put("missingCosts",BigDecimal.valueOf(offer.missingCosts().size()).multiply(weights.missingCosts()));
            BigDecimal score=parts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).setScale(4,RoundingMode.HALF_UP);
            ranked.add(new Ranked(offer,score,offer.currency(),Map.copyOf(parts)));
        }
        ranked.sort(Comparator.comparing((Ranked r)->r.rankingGroup().equals("USD")?"":r.rankingGroup())
            .thenComparing(Ranked::score).thenComparing(r->r.offer().amount()).thenComparing(r->r.offer().provider())
            .thenComparing(r->r.offer().providerOfferId()));
        return List.copyOf(ranked);
    }
    private static BigDecimal ratio(BigDecimal numerator,BigDecimal denominator){
        return denominator.signum()==0?BigDecimal.ZERO:numerator.divide(denominator,8,RoundingMode.HALF_UP);
    }
}
