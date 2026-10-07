package com.flighttripmanager.flightsearch.api.dto;
import java.time.Instant;
import java.util.UUID;
public record SavedOptionDto(UUID id,UUID tripId,UUID legId,int slot,CriteriaDto criteria,OfferDto initialOffer,
        boolean active,String inactiveReason,Instant createdAt,Instant updatedAt,long tripVersion) {}
