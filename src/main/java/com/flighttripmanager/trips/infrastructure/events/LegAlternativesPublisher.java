package com.flighttripmanager.trips.infrastructure.events;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import com.flighttripmanager.trips.application.port.out.LegAlternativesLifecycle;
import com.flighttripmanager.trips.application.contract.LegAlternativesClosed;
@Component
public class LegAlternativesPublisher implements LegAlternativesLifecycle {
    private final ApplicationEventPublisher events;
    public LegAlternativesPublisher(ApplicationEventPublisher events) { this.events=events; }
    @Override public void close(UUID tripId, UUID legId, String reason, Instant at) {
        events.publishEvent(new LegAlternativesClosed(tripId,legId,reason,at));
    }
}
