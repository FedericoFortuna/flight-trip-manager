package com.flighttripmanager.trips;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.application.mapper.PassengerMapper;
import com.flighttripmanager.trips.application.port.out.*;
import com.flighttripmanager.trips.application.usecase.PassengerService;
import com.flighttripmanager.trips.domain.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PassengerServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    private final TripStore trips = mock(TripStore.class);
    private final PassengerStore passengers = mock(PassengerStore.class);
    private final PassengerService service = new PassengerService(trips, passengers,
            Mappers.getMapper(PassengerMapper.class), Clock.fixed(NOW.minusSeconds(60), ZoneOffset.UTC));
    private final Trip trip = new Trip(UUID.randomUUID(), "Trip", LocalDate.parse("2026-10-01"),
            LocalDate.parse("2026-10-10"), null, null, 4, NOW, NOW, List.of());

    @Test void staleVersionCannotWritePassengerOrParent() {
        when(trips.find(trip.id(), true)).thenReturn(Optional.of(trip));
        assertThatThrownBy(() -> service.create(trip.id(), new CreatePassenger(3L, "A", "B", null)))
                .isInstanceOfSatisfying(TripsException.class, e -> assertThat(e.reason()).isEqualTo(TripsException.Reason.VERSION_CONFLICT));
        verifyNoInteractions(passengers);
        verify(trips, never()).saveHeader(any());
    }
    @Test void invalidVersionDoesNotReadPersistence() {
        for (Long version : Arrays.asList(null, -1L)) {
            assertThatThrownBy(() -> service.create(trip.id(), new CreatePassenger(version, "A", "B", null)))
                    .isInstanceOf(TripsException.class);
        }
        verifyNoInteractions(trips, passengers);
    }
    @Test void backwardsClockDoesNotMoveTimestampsBackAndDoesNotRewriteLegs() {
        when(trips.find(trip.id(), true)).thenReturn(Optional.of(trip));
        var result = service.create(trip.id(), new CreatePassenger(4L, " A ", " B ", null));
        assertThat(result.tripVersion()).isEqualTo(5);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        verify(trips).saveHeader(argThat(t -> t.version() == 5 && t.updatedAt().equals(NOW)));
        verify(trips, never()).save(any());
    }
    @Test void emptyApplicationPatchIsRejectedWithoutWrites() {
        when(trips.find(trip.id(), true)).thenReturn(Optional.of(trip));
        UUID id = UUID.randomUUID();
        when(passengers.find(trip.id(), id)).thenReturn(Optional.of(new Passenger(id, trip.id(), "A", "B", null, NOW, NOW)));
        assertThatThrownBy(() -> service.patch(trip.id(), id,
                new PatchPassenger(4L, Change.absent(), Change.absent(), Change.absent()))).isInstanceOf(TripsException.class);
        verify(passengers, never()).save(any());
        verify(trips, never()).saveHeader(any());
    }
}
