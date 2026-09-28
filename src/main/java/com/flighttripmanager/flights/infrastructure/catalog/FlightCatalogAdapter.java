package com.flighttripmanager.flights.infrastructure.catalog;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import com.flighttripmanager.catalog.application.port.in.CatalogLookup;
import com.flighttripmanager.catalog.application.contract.*;
import com.flighttripmanager.flights.application.port.out.FlightCatalog;
import com.flighttripmanager.flights.application.contract.FlightException;
import com.flighttripmanager.flights.domain.model.FlightSegment;
@Component
public class FlightCatalogAdapter implements FlightCatalog {
    private final CatalogLookup catalog;
    public FlightCatalogAdapter(CatalogLookup catalog) { this.catalog = catalog; }
    @Override public void validate(FlightSegment flight, FlightSegment old) {
        try {
            var airline = catalog.airlineById(flight.airlineId());
            var origin = catalog.airportById(flight.originAirportId());
            var destination = catalog.airportById(flight.destinationAirportId());
            if ((old == null || !flight.airlineId().equals(old.airlineId())) && !airline.active()) throw invalid("airlineId");
            if ((old == null || !flight.originAirportId().equals(old.originAirportId())) && !origin.active()) throw invalid("originAirportId");
            if ((old == null || !flight.destinationAirportId().equals(old.destinationAirportId())) && !destination.active()) throw invalid("destinationAirportId");
            if (old == null || !flight.flightNumber().equals(old.flightNumber()) || !flight.airlineId().equals(old.airlineId())) {
                boolean matches = matches(flight.flightNumber(), airline.iataCode()) || matches(flight.flightNumber(), airline.icaoCode());
                if (!matches) throw invalid("flightNumber");
            }
            if (flight.schedule().scheduledDeparture() != null
                    && !flight.schedule().scheduledDeparture().atZone(ZoneId.of(origin.timezone())).toLocalDate().equals(flight.flightDate())) {
                throw invalid("flightDate");
            }
        } catch (CatalogNotFoundException error) { throw invalid("catalog"); }
    }
    private boolean matches(String number, String code) {
        return code != null && number.startsWith(code) && number.substring(code.length()).matches("[0-9]{1,4}[A-Z]?");
    }
    private FlightException invalid(String field) { return new FlightException(FlightException.Reason.INVALID_REFERENCE, field); }
}
