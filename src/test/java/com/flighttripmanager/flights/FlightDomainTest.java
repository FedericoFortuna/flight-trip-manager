package com.flighttripmanager.flights;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.flighttripmanager.flights.domain.model.*;
import static org.assertj.core.api.Assertions.*;
class FlightDomainTest {
    private static final Instant NOW=Instant.parse("2026-09-28T00:00:00Z");
    private FlightSegment flight(String number, LocalDate date, List<FlightPassenger> passengers, FlightOperation operation) {
        return new FlightSegment(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),number,date,
            UUID.randomUUID(),UUID.randomUUID(),null,operation,null,null,passengers,null,null,NOW,NOW);
    }
    @Test void normalizesNumberAndDefaultsWithoutInventingTimesOrPassengers() {
        var result=flight(" ar1132 ",LocalDate.parse("2026-10-01"),null,null);
        assertThat(result.flightNumber()).isEqualTo("AR1132");
        assertThat(result.schedule().scheduledDeparture()).isNull();
        assertThat(result.connectionProtection()).isEqualTo(ConnectionProtection.UNKNOWN);
        assertThat(result.operation().status()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(result.passengers()).isEmpty();
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"x","AR-1132","AR123456","AR1132\nX"})
    void rejectsInvalidFlightNumbers(String number) {
        assertThatThrownBy(()->flight(number,LocalDate.parse("2026-10-01"),null,null)).isInstanceOf(FlightRuleViolation.class);
    }
    @Test void datesAndReferencesAreRequired() {
        assertThatThrownBy(()->flight("AR1",null,null,null)).isInstanceOf(FlightRuleViolation.class);
        assertThatThrownBy(()->flight("AR1",LocalDate.of(10000,1,1),null,null)).isInstanceOf(FlightRuleViolation.class);
        assertThatThrownBy(()->new FlightPassenger(null,null,null,null,null)).isInstanceOf(FlightRuleViolation.class);
    }
    @Test void assignmentsAreUniqueBoundedAndImmutable() {
        UUID id=UUID.randomUUID();
        var passenger=new FlightPassenger(id,"12A","1 bag","123456",null);
        assertThatThrownBy(()->flight("AR1",LocalDate.now(),List.of(passenger,passenger),null)).isInstanceOf(FlightRuleViolation.class);
        List<FlightPassenger> list=new ArrayList<>();
        list.add(passenger);
        var result=flight("AR1",LocalDate.now(),list,null);
        list.clear();
        assertThat(result.passengers()).hasSize(1);
        assertThatThrownBy(()->result.passengers().clear()).isInstanceOf(UnsupportedOperationException.class);
        var many=java.util.stream.IntStream.range(0,101).mapToObj(i->new FlightPassenger(UUID.randomUUID(),null,null,null,null)).toList();
        assertThatThrownBy(()->flight("AR1",LocalDate.now(),many,null)).isInstanceOf(FlightRuleViolation.class);
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.001","100000000000000000"})
    void rejectsInvalidAmounts(String amount) {
        assertThatThrownBy(()->new FlightMoney(new BigDecimal(amount),"USD",null)).isInstanceOf(FlightRuleViolation.class);
    }
    @Test void monetaryValuesRemainExactAndUnknownConversionRemainsNull() {
        var foreign=new FlightMoney(new BigDecimal("12345678901234567.89"),"eur",null);
        assertThat(foreign.amount()).isEqualByComparingTo("12345678901234567.89");
        assertThat(foreign.currency()).isEqualTo("EUR");
        assertThat(foreign.amountUsd()).isNull();
        assertThat(new FlightMoney(BigDecimal.TEN,"USD",null).amountUsd()).isEqualByComparingTo("10");
        assertThatThrownBy(()->new FlightMoney(BigDecimal.TEN,"USD",BigDecimal.ONE)).isInstanceOf(FlightRuleViolation.class);
        assertThatThrownBy(()->new FlightMoney(BigDecimal.TEN,"BAD",null)).isInstanceOf(FlightRuleViolation.class);
        assertThatThrownBy(()->new FlightMoney(null,"USD",null)).isInstanceOf(FlightRuleViolation.class);
    }
    @Test void validatesSchedulePairsAndDatabasePrecision() {
        new FlightSchedule(null,null,NOW,null,null,null);
        assertThatThrownBy(()->new FlightSchedule(NOW,NOW.minusSeconds(1),null,null,null,null)).isInstanceOf(FlightRuleViolation.class);
        assertThatThrownBy(()->new FlightSchedule(null,null,NOW,NOW.minusSeconds(1),null,null)).isInstanceOf(FlightRuleViolation.class);
        assertThatThrownBy(()->new FlightSchedule(null,null,null,null,NOW,NOW.minusSeconds(1))).isInstanceOf(FlightRuleViolation.class);
        assertThatThrownBy(()->new FlightSchedule(NOW.plusNanos(1),null,null,null,null,null)).isInstanceOf(FlightRuleViolation.class);
        assertThatThrownBy(()->new FlightSchedule(Instant.MAX,null,null,null,null,null)).isInstanceOf(FlightRuleViolation.class);
    }
    @Test void historyIncludesOnlyChangedOperationalFieldsAndNumber() {
        var old=flight("AR1",LocalDate.now(),null,null);
        var next=new FlightSegment(old.id(),old.tripId(),old.tripLegId(),old.airlineId(),"AR2",old.flightDate(),
            old.originAirportId(),old.destinationAirportId(),old.schedule(),new FlightOperation(FlightStatus.CANCELLED,"A","2",null,null),
            new FlightBooking("SECRET","123",null,null,null,null),old.connectionProtection(),old.passengers(),null,null,NOW,NOW);
        var changes=FlightHistory.changes(old,next,8);
        assertThat(changes).extracting(FlightHistory::field).containsExactly("flightNumber","status","departureTerminal","departureGate");
        assertThat(changes).allMatch(h->h.revision()==8);
        assertThat(changes.toString()).doesNotContain("SECRET");
        assertThat(FlightHistory.changes(next,next,9)).isEmpty();
    }
    @ParameterizedTest @EnumSource(FlightStatus.class)
    void preservesExplicitManualStatus(FlightStatus status) {
        assertThat(flight("AR1",LocalDate.now(),null,new FlightOperation(status,null,null,null,null)).operation().status()).isEqualTo(status);
    }
    @Test void sensitiveValueObjectsDoNotExposeBookingDetailsInToString() {
        assertThat(new FlightBooking("SECRET","TICKET",null,null,null,null).toString()).doesNotContain("SECRET","TICKET");
        assertThat(new FlightPassenger(UUID.randomUUID(),null,null,"TICKET",null).toString()).doesNotContain("TICKET");
        assertThatThrownBy(()->new FlightBooking("x".repeat(33),null,null,null,null,null)).isInstanceOf(FlightRuleViolation.class);
        assertThatThrownBy(()->new FlightOperation(null,"x".repeat(41),null,null,null)).isInstanceOf(FlightRuleViolation.class);
    }
}
