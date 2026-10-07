package com.flighttripmanager.flightsearch.domain.model;
import java.time.Instant;
import java.util.UUID;
public record SavedFlightOption(UUID id, UUID tripId, UUID legId, int slot, SearchCriteria criteria,
        SearchOffer initialOffer, boolean active, String inactiveReason, Instant createdAt, Instant updatedAt) {
    public SavedFlightOption {
        if(id==null||tripId==null||legId==null||slot<1||slot>3||criteria==null||initialOffer==null||createdAt==null
                ||updatedAt==null||updatedAt.isBefore(createdAt)||active!=(inactiveReason==null))
            throw new SearchRuleViolation("savedOption");
    }
    public SavedFlightOption deactivate(String reason,Instant now){
        return new SavedFlightOption(id,tripId,legId,slot,criteria,initialOffer,false,reason,createdAt,now.isBefore(updatedAt)?updatedAt:now);
    }
    public boolean sameProduct(SearchCriteria other,SearchOffer offer){
        return initialOffer.provider().equals(offer.provider()) && initialOffer.ownerAirline().equals(offer.ownerAirline())
            && initialOffer.synthetic()==offer.synthetic() && initialOffer.testMode()==offer.testMode()
            && initialOffer.slices().equals(offer.slices()) && initialOffer.checkedBag()==offer.checkedBag()
            && criteria.adults()==other.adults() && criteria.childAges().equals(other.childAges()) && criteria.cabin()==other.cabin();
    }
}
