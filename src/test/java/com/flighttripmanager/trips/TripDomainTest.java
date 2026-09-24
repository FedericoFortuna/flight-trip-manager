package com.flighttripmanager.trips;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.flighttripmanager.trips.domain.model.*;
import static org.assertj.core.api.Assertions.*;

class TripDomainTest {
    private static final UUID TRIP = UUID.randomUUID();
    private static final Place ORIGIN = new Place(PlaceType.LOCATION, UUID.randomUUID());
    private static final Place DESTINATION = new Place(PlaceType.AIRPORT, UUID.randomUUID());
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-06-01");

    @Test void dateOnlyRemainsUnknownHourAndInstantDerivesUtcDate() {
        var dateOnly = leg(DAY, null, DAY.plusDays(1), null, LegStatus.BOOKED, null, CREATED);
        assertThat(dateOnly.departureDateTime()).isNull();
        assertThat(dateOnly.arrivalDateTime()).isNull();
        var exact = leg(null, Instant.parse("2026-06-01T23:00:00Z"), null, Instant.parse("2026-06-02T01:00:00Z"),
                LegStatus.BOOKED, null, CREATED);
        assertThat(exact.departureDate()).isEqualTo(DAY);
        assertThat(exact.arrivalDate()).isEqualTo(DAY.plusDays(1));
    }

    @ParameterizedTest @EnumSource(value = LegStatus.class, names = "PLANNED", mode = EnumSource.Mode.EXCLUDE)
    void undatedLegIsOnlyAllowedWhilePlanned(LegStatus status) {
        assertThatThrownBy(() -> leg(null, null, null, null, status, null, CREATED)).isInstanceOf(TripRuleViolation.class);
    }

    @Test void scheduleRejectsInconsistencyAndExcessPrecision() {
        assertThatThrownBy(() -> leg(null, null, DAY, null, LegStatus.PLANNED, null, CREATED)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> leg(DAY, null, DAY.minusDays(1), null, LegStatus.PLANNED, null, CREATED)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> leg(DAY, Instant.parse("2026-06-02T00:00:00Z"), null, null, LegStatus.PLANNED, null, CREATED))
                .isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> leg(null, Instant.parse("2026-06-01T10:00:00.123456789Z"), null, null, LegStatus.PLANNED, null, CREATED))
                .isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> leg(null, Instant.parse("2026-06-01T10:00:00Z"), null, Instant.parse("2026-06-01T09:00:00Z"),
                LegStatus.PLANNED, null, CREATED)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> leg(LocalDate.of(0, 1, 1), null, null, null, LegStatus.PLANNED, null, CREATED)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> leg(null, Instant.MAX, null, null, LegStatus.PLANNED, null, CREATED)).isInstanceOf(TripRuleViolation.class);
    }

    @Test void endpointsMustBeDistinctAndComplete() {
        assertThatThrownBy(() -> new Place(null, UUID.randomUUID())).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> new Place(PlaceType.AIRPORT, null)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> new TripLeg(UUID.randomUUID(), TRIP, ORIGIN, ORIGIN, TransportType.CAR,
                null, null, null, null, LegStatus.PLANNED, null, CREATED, CREATED)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> new TripLeg(UUID.randomUUID(), TRIP, null, DESTINATION, TransportType.CAR,
                null, null, null, null, LegStatus.PLANNED, null, CREATED, CREATED)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> leg(null, null, null, null, LegStatus.PLANNED, -1, CREATED)).isInstanceOf(TripRuleViolation.class);
    }

    @Test void automaticOrderIsChronologicalWithUnknownDatesAndHoursLast() {
        var undated = leg(null, null, null, null, LegStatus.PLANNED, null, CREATED);
        var unknownHour = leg(DAY, null, null, null, LegStatus.PLANNED, null, CREATED);
        var late = leg(DAY, Instant.parse("2026-06-01T12:00:00Z"), null, null, LegStatus.PLANNED, null, CREATED);
        var early = leg(DAY, Instant.parse("2026-06-01T09:00:00Z"), null, null, LegStatus.PLANNED, null, CREATED);
        var otherUnknown = leg(DAY, null, null, null, LegStatus.PLANNED, null, CREATED.plusSeconds(1));
        assertThat(trip(List.of(undated, late, unknownHour, early, otherUnknown), null).orderedLegs())
                .containsExactly(early, late, unknownHour, otherUnknown, undated);
    }

    @Test void uuidBreaksTiesDeterministicallyAndManualOrderOverridesDates() {
        var first = leg(DAY, null, null, null, LegStatus.PLANNED, null, CREATED);
        var second = leg(DAY, null, null, null, LegStatus.PLANNED, null, CREATED);
        var expected = new ArrayList<>(List.of(first, second));
        expected.sort(Comparator.comparing(TripLeg::id));
        assertThat(trip(List.of(second, first), null).orderedLegs()).containsExactlyElementsOf(expected);
        assertThat(trip(List.of(first.ordered(1, CREATED), second.ordered(0, CREATED)), null).orderedLegs())
                .extracting(TripLeg::id).containsExactly(second.id(), first.id());
        assertThatThrownBy(() -> trip(List.of(first.ordered(0, CREATED), second), null)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> trip(List.of(first.ordered(0, CREATED), second.ordered(0, CREATED)), null)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> trip(List.of(first.ordered(2, CREATED)), null)).isInstanceOf(TripRuleViolation.class);
    }

    @ParameterizedTest @CsvSource({
            "2026-05-31T23:59:59Z,UPCOMING",
            "2026-06-01T10:00:00Z,IN_PROGRESS",
            "2026-06-01T11:00:00Z,COMPLETED"})
    void bookedExactLegDerivesStatusAtTemporalBoundaries(String now, LegStatus expected) {
        var leg = leg(null, Instant.parse("2026-06-01T10:00:00Z"), null, Instant.parse("2026-06-01T11:00:00Z"),
                LegStatus.BOOKED, null, CREATED);
        assertThat(leg.status(Instant.parse(now))).isEqualTo(expected);
    }

    @Test void dateOnlyArrivalCompletesAfterTheDayAndNoArrivalStaysInProgress() {
        var known = leg(DAY, null, DAY, null, LegStatus.UPCOMING, null, CREATED);
        assertThat(known.status(Instant.parse("2026-06-01T23:59:59Z"))).isEqualTo(LegStatus.IN_PROGRESS);
        assertThat(known.status(Instant.parse("2026-06-02T00:00:00Z"))).isEqualTo(LegStatus.COMPLETED);
        assertThat(leg(DAY, null, null, null, LegStatus.BOOKED, null, CREATED).status(Instant.parse("2026-06-02T00:00:00Z")))
                .isEqualTo(LegStatus.IN_PROGRESS);
    }

    @Test void tripAggregationHandlesEmptyCancelledPartialCompletedAndOverrides() {
        assertThat(trip(List.of(), null).status(CREATED)).isEqualTo(TripStatus.PLANNING);
        var booked = leg(DAY, null, null, null, LegStatus.BOOKED, null, CREATED);
        var planned = leg(null, null, null, null, LegStatus.PLANNED, null, CREATED);
        var cancelled = leg(DAY, null, null, null, LegStatus.CANCELLED, null, CREATED);
        var complete = leg(DAY, null, null, null, LegStatus.COMPLETED, null, CREATED);
        assertThat(trip(List.of(cancelled), null).status(CREATED)).isEqualTo(TripStatus.CANCELLED);
        assertThat(trip(List.of(booked, planned), null).status(CREATED)).isEqualTo(TripStatus.PARTIALLY_BOOKED);
        assertThat(trip(List.of(complete, cancelled), null).status(CREATED)).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip(List.of(booked), TripStatus.CANCELLED).status(CREATED)).isEqualTo(TripStatus.CANCELLED);
        assertThat(trip(List.of(booked), TripStatus.CANCELLED).derivedStatus(CREATED)).isEqualTo(TripStatus.UPCOMING);
        assertThat(trip(List.of(booked), null).status(Instant.parse("2026-06-02T00:00:00Z"))).isEqualTo(TripStatus.IN_PROGRESS);
    }

    @Test void allBookedFutureTripIsUpcomingRatherThanBooked() {
        var leg = leg(DAY, null, null, null, LegStatus.BOOKED, null, CREATED);
        assertThat(trip(List.of(leg), null).status(CREATED)).isEqualTo(TripStatus.UPCOMING);
    }

    @ParameterizedTest @ValueSource(strings = {"-0.01", "1.001", "100000000000000000.00"})
    void budgetRejectsNegativeUnrepresentableAndOversizedAmounts(String amount) {
        assertThatThrownBy(() -> new Trip(TRIP, "Trip", DAY, DAY, new BigDecimal(amount), null, 0, CREATED, CREATED, List.of()))
                .isInstanceOf(TripRuleViolation.class);
    }

    @Test void aggregateEnforcesDateWindowIdentityAndLimits() {
        var outside = leg(DAY.minusDays(1), null, null, null, LegStatus.PLANNED, null, CREATED);
        assertThatThrownBy(() -> trip(List.of(outside), null)).isInstanceOf(TripRuleViolation.class);
        var arrivalOutside = leg(DAY, null, DAY.plusDays(11), null, LegStatus.PLANNED, null, CREATED);
        assertThatThrownBy(() -> trip(List.of(arrivalOutside), null)).isInstanceOf(TripRuleViolation.class);
        var leg = leg(DAY, null, null, null, LegStatus.PLANNED, null, CREATED);
        assertThatThrownBy(() -> trip(List.of(leg, leg), null)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> trip(Collections.nCopies(501, leg), null)).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> new Trip(TRIP, " ", DAY, DAY, null, null, 0, CREATED, CREATED, List.of())).isInstanceOf(TripRuleViolation.class);
        assertThatThrownBy(() -> new Trip(TRIP, "Trip", DAY, DAY.minusDays(1), null, null, 0, CREATED, CREATED, List.of())).isInstanceOf(TripRuleViolation.class);
        assertThat(new Trip(TRIP, " Trip ", DAY, DAY, BigDecimal.ZERO, null, 0, CREATED, CREATED, List.of()).name()).isEqualTo("Trip");
        var changed = trip(List.of(), null).withLegs(List.of(leg), CREATED.plusSeconds(1));
        assertThat(changed.version()).isEqualTo(1);
        assertThatThrownBy(() -> changed.legs().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static Trip trip(List<TripLeg> legs, TripStatus override) {
        return new Trip(TRIP, "Trip", DAY, DAY.plusDays(10), null, override, 0, CREATED, CREATED, legs);
    }
    private static TripLeg leg(LocalDate departure, Instant departureTime, LocalDate arrival, Instant arrivalTime,
            LegStatus status, Integer order, Instant created) {
        return new TripLeg(UUID.randomUUID(), TRIP, ORIGIN, DESTINATION, TransportType.FLIGHT,
                departure, departureTime, arrival, arrivalTime, status, order, created, created);
    }
}
