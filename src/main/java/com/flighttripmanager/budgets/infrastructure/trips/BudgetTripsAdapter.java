package com.flighttripmanager.budgets.infrastructure.trips;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.flighttripmanager.budgets.application.port.out.BudgetTrips;
import com.flighttripmanager.trips.application.port.in.TripBudgetAccess;
@Component
public class BudgetTripsAdapter implements BudgetTrips {
    private final TripBudgetAccess trips;
    public BudgetTripsAdapter(TripBudgetAccess trips){this.trips=trips;}
    @Override public Context inspect(UUID tripId,boolean lock){
        var c=trips.inspectBudget(tripId,lock);
        return new Context(c.version(),c.updatedAt(),c.totalBudgetUsd(),c.legs().stream().map(l->new Leg(l.id(),l.transportType())).toList());
    }
    @Override public long update(UUID tripId,long version,BigDecimal total){return trips.updateBudget(tripId,version,total);}
}
