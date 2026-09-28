package com.flighttripmanager.flights.api.response;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.flighttripmanager.flights.application.contract.*;
public record OperationResponse(FlightStatusValue status, String departureTerminal, String departureGate, String arrivalTerminal, String arrivalGate) {}
