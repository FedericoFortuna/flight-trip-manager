package com.flighttripmanager.flights.application.contract;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
public record FlightPatch(Long version, FlightChange<UUID> airlineId, FlightChange<String> flightNumber, FlightChange<LocalDate> flightDate, FlightChange<UUID> originAirportId, FlightChange<UUID> destinationAirportId, FlightChange<ScheduleData> schedule, FlightChange<OperationData> operation, FlightChange<BookingData> booking, FlightChange<ConnectionProtectionValue> connectionProtection, FlightChange<List<TravelerData>> passengers) {
    public FlightData apply(FlightData old) { return new FlightData(airlineId.apply(old.airlineId()), flightNumber.apply(old.flightNumber()), flightDate.apply(old.flightDate()), originAirportId.apply(old.originAirportId()), destinationAirportId.apply(old.destinationAirportId()), schedule.apply(old.schedule()), operation.apply(old.operation()), booking.apply(old.booking()), connectionProtection.apply(old.connectionProtection()), passengers.apply(old.passengers())); }
}
