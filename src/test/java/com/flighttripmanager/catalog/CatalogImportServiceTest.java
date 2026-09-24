package com.flighttripmanager.catalog;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import com.flighttripmanager.catalog.application.contract.*;
import com.flighttripmanager.catalog.application.mapper.CatalogImportDomainMapper;
import com.flighttripmanager.catalog.application.port.out.*;
import com.flighttripmanager.catalog.application.usecase.CatalogImportService;
import com.flighttripmanager.catalog.domain.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogImportServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00.123456789Z");
    private static final Instant OBSERVED = Instant.parse("2026-09-20T00:00:00Z");
    private final CatalogImportStore store = mock(CatalogImportStore.class);
    private final CatalogImportService service = new CatalogImportService(store,
            Mappers.getMapper(CatalogImportDomainMapper.class), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test void normalizesValuesAndUsesInjectedClockForSynchronization() {
        var result = service.importBatch(batch());
        assertThat(result.created()).isEqualTo(3);
        var airport = ArgumentCaptor.forClass(Airport.class);
        var stamp = ArgumentCaptor.forClass(ImportMetadata.class);
        verify(store).save(airport.capture(), stamp.capture());
        assertThat(airport.getValue().iataCode()).isEqualTo("AAA");
        assertThat(airport.getValue().latitude()).isEqualTo(new BigDecimal("-34.100000"));
        assertThat(airport.getValue().lastSyncedAt()).isEqualTo(Instant.parse("2026-09-23T12:00:00.123456Z"));
        assertThat(stamp.getValue().source()).isEqualTo("fixture");
        assertThat(stamp.getValue().externalId()).isEqualTo("airport-1");
        assertThat(stamp.getValue().observedAt()).isEqualTo(OBSERVED);
        var order = inOrder(store);
        order.verify(store).acquireImportLock();
        order.verify(store).airport("fixture", "airport-1");
        assertThatThrownBy(() -> result.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void matchingVersionAndValuesDoNotCallAnyWritePort() {
        var mapper = Mappers.getMapper(CatalogImportDomainMapper.class);
        var input = batch();
        var prior = NOW.minusSeconds(60);
        var airport = mapper.airport(input.airports().get(0), UUID.randomUUID(), prior);
        var airline = mapper.airline(input.airlines().get(0), UUID.randomUUID(), prior);
        var location = mapper.location(input.locations().get(0), UUID.randomUUID());
        when(store.airport("fixture", "airport-1")).thenReturn(Optional.of(new StoredCatalogEntry<>(airport, OBSERVED)));
        when(store.airline("fixture", "airline-1")).thenReturn(Optional.of(new StoredCatalogEntry<>(airline, OBSERVED)));
        when(store.location("fixture", "location-1")).thenReturn(Optional.of(new StoredCatalogEntry<>(location, OBSERVED)));
        var result = service.importBatch(input);
        assertThat(result.unchanged()).isEqualTo(3);
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.items()).extracting(ImportItemResult::id).containsExactly(airport.id(), airline.id(), location.id());
        verify(store, never()).save(any(Airport.class), any());
        verify(store, never()).save(any(Airline.class), any());
        verify(store, never()).save(any(Location.class), any());
    }

    @ParameterizedTest @MethodSource("invalidBatches")
    void invalidInputNeverAcquiresDatabaseLockOrWrites(CatalogImportBatch batch) {
        assertThatThrownBy(() -> service.importBatch(batch)).isInstanceOfSatisfying(CatalogImportException.class,
                exception -> assertThat(exception.reason()).isEqualTo(CatalogImportException.Reason.INVALID_BATCH));
        verifyNoInteractions(store);
    }

    static Stream<CatalogImportBatch> invalidBatches() {
        var valid = batch();
        var airport = valid.airports().get(0);
        var location = valid.locations().get(0);
        return Stream.of(
                null,
                new CatalogImportBatch(null, OBSERVED, valid.airports(), null, null),
                new CatalogImportBatch("x".repeat(65), OBSERVED, valid.airports(), null, null),
                new CatalogImportBatch("fixture", null, valid.airports(), null, null),
                new CatalogImportBatch("fixture", NOW.plusSeconds(1), valid.airports(), null, null),
                new CatalogImportBatch("fixture", NOW, valid.airports(), null, null),
                new CatalogImportBatch("fixture", Instant.EPOCH.minusSeconds(1), valid.airports(), null, null),
                new CatalogImportBatch("fixture", OBSERVED, null, null, null),
                new CatalogImportBatch("fixture", OBSERVED, Collections.nCopies(501, airport), null, null),
                new CatalogImportBatch("fixture", OBSERVED, Arrays.asList((AirportImport) null), null, null),
                new CatalogImportBatch("fixture", OBSERVED, List.of(airport, airport), null, null),
                new CatalogImportBatch("fixture", OBSERVED, List.of(new AirportImport("a", "AAA", null, "Airport", "City", "AR",
                        null, null, "UTC", null)), null, null),
                new CatalogImportBatch("fixture", OBSERVED, List.of(new AirportImport("a", "AAA", null, "Airport", "City", "AR",
                        null, null, "Missing/Zone", true)), null, null),
                new CatalogImportBatch("fixture", OBSERVED, List.of(new AirportImport("a", "AAA", null, "Airport", "City", "AR",
                        new BigDecimal("1.1234567"), BigDecimal.ZERO, "UTC", true)), null, null),
                new CatalogImportBatch("fixture", OBSERVED, null, List.of(new AirlineImport("a", "A1", null, "Airline", "AR", null)), null),
                new CatalogImportBatch("fixture", OBSERVED, null, null, List.of(new LocationImport("l", null, "City", "City", "AR"))),
                new CatalogImportBatch("fixture", OBSERVED, null, null, List.of(new LocationImport(null, LocationKind.CITY, "City", "City", "AR"))),
                new CatalogImportBatch("fixture", OBSERVED, null, null, List.of(new LocationImport("l", location.type(), " ", "City", "AR")))
        );
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "a\nb", "a\u0000b"})
    void invalidExternalIdsAreRejected(String id) {
        assertThatThrownBy(() -> service.importBatch(new CatalogImportBatch("fixture", OBSERVED, null, null,
                List.of(new LocationImport(id, LocationKind.CITY, "City", "City", "AR")))))
                .isInstanceOf(CatalogImportException.class);
        verifyNoInteractions(store);
    }

    @Test void longExternalIdsAreRejected() {
        assertThatThrownBy(() -> service.importBatch(new CatalogImportBatch("fixture", OBSERVED, null, null,
                List.of(new LocationImport("x".repeat(101), LocationKind.CITY, "City", "City", "AR")))))
                .isInstanceOf(CatalogImportException.class);
        verifyNoInteractions(store);
    }

    @Test void explicitNullCoordinatesAndBlankOptionalIcaoRemainUnknown() {
        service.importBatch(new CatalogImportBatch("fixture", OBSERVED,
                List.of(new AirportImport("a", "AAA", " ", "Airport", "City", "AR", null, null, "UTC", true)), null, null));
        var airport = ArgumentCaptor.forClass(Airport.class);
        verify(store).save(airport.capture(), any());
        assertThat(airport.getValue().icaoCode()).isNull();
        assertThat(airport.getValue().latitude()).isNull();
        assertThat(airport.getValue().longitude()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"TRAIN_STATION", "BUS_STATION"})
    void stationKindsAreMappedWithoutCreatingAirports(String kind) {
        service.importBatch(new CatalogImportBatch("fixture", OBSERVED, null, null,
                List.of(new LocationImport("station", LocationKind.valueOf(kind), "Station", "City", "AR"))));
        var location = ArgumentCaptor.forClass(Location.class);
        verify(store).save(location.capture(), any());
        assertThat(location.getValue().type().name()).isEqualTo(kind);
        verify(store, never()).save(any(Airport.class), any());
    }

    private static CatalogImportBatch batch() {
        return new CatalogImportBatch(" Fixture ", OBSERVED,
                List.of(new AirportImport(" airport-1 ", " aaa ", " aaaa ", "Airport", "City", " ar ",
                        new BigDecimal("-34.1"), new BigDecimal("-58.2"), "UTC", true)),
                List.of(new AirlineImport("airline-1", "a1", "aaa", "Airline", "ar", true)),
                List.of(new LocationImport("location-1", LocationKind.CITY, "City", "City", "ar")));
    }
}
