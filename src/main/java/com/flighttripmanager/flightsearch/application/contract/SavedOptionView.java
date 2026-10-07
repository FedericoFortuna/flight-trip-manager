package com.flighttripmanager.flightsearch.application.contract;
import java.time.Instant;
import java.util.UUID;
public record SavedOptionView(UUID id,UUID tripId,UUID legId,int slot,CriteriaData criteria,OfferData initialOffer,
        boolean active,String inactiveReason,Instant createdAt,Instant updatedAt,long tripVersion) {}
