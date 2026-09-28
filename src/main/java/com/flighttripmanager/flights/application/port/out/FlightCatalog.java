package com.flighttripmanager.flights.application.port.out;
import com.flighttripmanager.flights.domain.model.FlightSegment;
public interface FlightCatalog { void validate(FlightSegment flight, FlightSegment previous); }
