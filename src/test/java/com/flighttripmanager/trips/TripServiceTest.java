package com.flighttripmanager.trips;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import com.flighttripmanager.trips.application.contract.*;
import com.flighttripmanager.trips.application.mapper.TripMapper;
import com.flighttripmanager.trips.application.port.out.*;
import com.flighttripmanager.trips.application.usecase.TripService;
import com.flighttripmanager.trips.domain.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TripServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final TripStore store = mock(TripStore.class);
    private final CatalogPlaces places = mock(CatalogPlaces.class);
    private final TripService service = new TripService(store, places, Mappers.getMapper(TripMapper.class),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final Trip trip = new Trip(UUID.randomUUID(), "Trip", LocalDate.parse("2026-06-01"),
            LocalDate.parse("2026-06-10"), null, null, 5, NOW, NOW, List.of());

    @Test void staleVersionPreventsCatalogAccessAndWrites() {
        when(store.find(trip.id(), true)).thenReturn(Optional.of(trip));
        assertThatThrownBy(() -> service.addLeg(trip.id(), command(4L)))
                .isInstanceOfSatisfying(TripsException.class, error ->
                        assertThat(error.reason()).isEqualTo(TripsException.Reason.VERSION_CONFLICT));
        verifyNoInteractions(places);
        verify(store, never()).save(any());
    }
    @Test void referenceFailureNeverSavesPartialAggregate() {
        when(store.find(trip.id(), true)).thenReturn(Optional.of(trip));
        doThrow(new TripsException(TripsException.Reason.INVALID_REFERENCE, "place")).when(places).requireUsable(any());
        assertThatThrownBy(() -> service.addLeg(trip.id(), command(5L))).isInstanceOf(TripsException.class);
        verify(store, never()).save(any());
    }
    @Test void missingVersionAndMissingTripAreDistinct() {
        assertThatThrownBy(() -> service.addLeg(trip.id(), command(null)))
                .isInstanceOfSatisfying(TripsException.class, error ->
                        assertThat(error.reason()).isEqualTo(TripsException.Reason.INVALID_REQUEST));
        verifyNoInteractions(store);
        when(store.find(trip.id(), false)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(trip.id())).isInstanceOfSatisfying(TripsException.class, error ->
                assertThat(error.reason()).isEqualTo(TripsException.Reason.TRIP_NOT_FOUND));
    }
    @Test void omittedLegStatusDefaultsToPlannedAndAggregateVersionAdvances() {
        when(store.find(trip.id(), true)).thenReturn(Optional.of(trip));
        var result = service.addLeg(trip.id(), command(5L));
        assertThat(result.status()).isEqualTo(LegStatusValue.PLANNED);
        assertThat(result.tripVersion()).isEqualTo(6);
        var capture = org.mockito.ArgumentCaptor.forClass(Trip.class);
        verify(store).save(capture.capture());
        assertThat(capture.getValue().legs()).hasSize(1);
        assertThat(capture.getValue().createdAt()).isEqualTo(NOW);
    }
    private CreateLeg command(Long version) {
        return new CreateLeg(version, new PlaceRef(PlaceTypeValue.LOCATION, UUID.randomUUID()),
                new PlaceRef(PlaceTypeValue.AIRPORT, UUID.randomUUID()), TransportTypeValue.FLIGHT,
                null, null, null, null, null);
    }
}
