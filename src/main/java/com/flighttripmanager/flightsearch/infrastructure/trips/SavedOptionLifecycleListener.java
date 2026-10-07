package com.flighttripmanager.flightsearch.infrastructure.trips;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.*;
import com.flighttripmanager.trips.application.contract.LegAlternativesClosed;
import com.flighttripmanager.flightsearch.application.port.out.SavedOptionStore;
@Component
public class SavedOptionLifecycleListener {
    private final SavedOptionStore store;
    public SavedOptionLifecycleListener(SavedOptionStore store){this.store=store;}
    // Synchronous listener: closure commits or rolls back with the trip revision.
    @EventListener @Transactional(propagation=Propagation.MANDATORY)
    public void close(LegAlternativesClosed event){store.close(event.tripId(),event.legId(),event.reason(),event.at());}
}
