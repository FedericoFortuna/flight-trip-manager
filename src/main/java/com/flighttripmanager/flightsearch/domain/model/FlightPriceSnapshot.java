package com.flighttripmanager.flightsearch.domain.model;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record FlightPriceSnapshot(UUID id, UUID savedFlightOptionId, UUID sourceSearchId, String providerOfferId,
        BigDecimal basePrice, BigDecimal baggagePrice, BigDecimal seatPrice, BigDecimal totalPrice,
        String originalCurrency, BigDecimal originalAmount, BigDecimal amountUsd, String provider,
        Instant observedAt, boolean synthetic, boolean testMode, List<String> missingCosts) {
    public FlightPriceSnapshot {
        if(id==null||savedFlightOptionId==null||sourceSearchId==null||providerOfferId==null||provider==null||observedAt==null
            ||totalPrice==null||originalAmount==null||!totalPrice.equals(originalAmount))throw new SearchRuleViolation("snapshot");
        for(var amount:new BigDecimal[]{basePrice,baggagePrice,seatPrice,totalPrice,originalAmount,amountUsd})
            if(amount!=null&&(amount.signum()<0||amount.scale()>4||(long)amount.precision()-amount.scale()>19))throw new SearchRuleViolation("price");
        try{Currency.getInstance(originalCurrency);}catch(IllegalArgumentException|NullPointerException e){throw new SearchRuleViolation("currency");}
        if("USD".equals(originalCurrency)&&(amountUsd==null||amountUsd.compareTo(originalAmount)!=0))throw new SearchRuleViolation("amountUsd");
        basePrice=scale(basePrice);baggagePrice=scale(baggagePrice);seatPrice=scale(seatPrice);
        totalPrice=scale(totalPrice);originalAmount=scale(originalAmount);amountUsd=scale(amountUsd);
        observedAt=observedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        missingCosts=List.copyOf(missingCosts);
    }
    private static BigDecimal scale(BigDecimal amount){return amount==null?null:amount.setScale(4);}
    public static FlightPriceSnapshot observed(UUID optionId,UUID searchId,SearchOffer offer){
        // Provider total does not disclose base fare or ancillary allocations. Preserve unknowns.
        return new FlightPriceSnapshot(UUID.randomUUID(),optionId,searchId,offer.providerOfferId(),null,null,null,
            offer.amount(),offer.currency(),offer.amount(),offer.amountUsd(),offer.provider(),offer.observedAt(),
            offer.synthetic(),offer.testMode(),offer.missingCosts());
    }
}
