package com.flighttripmanager.trips.application.usecase;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.flighttripmanager.trips.application.port.in.TripBudgetAccess;
import com.flighttripmanager.trips.application.port.out.TripStore;
import com.flighttripmanager.trips.application.contract.*;
import static com.flighttripmanager.trips.application.contract.TripsException.Reason.*;
@Service
public class TripBudgetService implements TripBudgetAccess {
    private final TripStore trips;private final Clock clock;
    public TripBudgetService(TripStore trips,Clock clock){this.trips=trips;this.clock=clock;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public BudgetTripContext inspectBudget(UUID tripId,boolean lock){
        var trip=trips.find(tripId,lock).orElseThrow(()->new TripsException(TRIP_NOT_FOUND,"tripId"));
        return new BudgetTripContext(trip.version(),trip.updatedAt(),trip.totalBudgetUsd(),
            trip.legs().stream().map(l->new BudgetTripContext.Leg(l.id(),l.transportType().name())).toList());
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public long updateBudget(UUID tripId,long version,BigDecimal total){
        var trip=trips.find(tripId,true).orElseThrow(()->new TripsException(TRIP_NOT_FOUND,"tripId"));
        if(trip.version()!=version)throw new TripsException(VERSION_CONFLICT,"version");
        Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);if(now.isBefore(trip.updatedAt()))now=trip.updatedAt();
        var changed=trip.changed(trip.name(),trip.startDate(),trip.endDate(),total,trip.manualStatusOverride(),trip.legs(),now);
        trips.saveHeader(changed);return changed.version();
    }
}
