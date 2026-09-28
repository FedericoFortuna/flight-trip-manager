ALTER TABLE trips.trip_legs ADD CONSTRAINT leg_trip_type_identity UNIQUE (trip_id, id, transport_type);
ALTER TABLE trips.passengers ADD CONSTRAINT passenger_trip_identity UNIQUE (trip_id, id);

CREATE TABLE flights.flight_segments (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL,
    trip_leg_id uuid NOT NULL,
    transport_type varchar(20) NOT NULL CHECK (transport_type = 'FLIGHT'),
    FOREIGN KEY (trip_id, trip_leg_id, transport_type)
        REFERENCES trips.trip_legs(trip_id, id, transport_type) ON DELETE RESTRICT ON UPDATE RESTRICT,
    UNIQUE (trip_id, id),
    airline_id uuid NOT NULL REFERENCES catalog.airlines(id) ON DELETE RESTRICT,
    flight_number varchar(10) NOT NULL CHECK (flight_number ~ '^[A-Z0-9]{2,3}[0-9]{1,4}[A-Z]?$'),
    flight_date date NOT NULL CHECK (flight_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    origin_airport_id uuid NOT NULL REFERENCES catalog.airports(id) ON DELETE RESTRICT,
    destination_airport_id uuid NOT NULL REFERENCES catalog.airports(id) ON DELETE RESTRICT,
    CHECK (origin_airport_id <> destination_airport_id),
    scheduled_departure timestamptz, scheduled_arrival timestamptz,
    estimated_departure timestamptz, estimated_arrival timestamptz,
    actual_departure timestamptz, actual_arrival timestamptz,
    original_scheduled_departure timestamptz, original_scheduled_arrival timestamptz,
    CHECK (scheduled_arrival IS NULL OR scheduled_departure IS NULL OR scheduled_arrival >= scheduled_departure),
    CHECK (estimated_arrival IS NULL OR estimated_departure IS NULL OR estimated_arrival >= estimated_departure),
    CHECK (actual_arrival IS NULL OR actual_departure IS NULL OR actual_arrival >= actual_departure),
    CHECK (original_scheduled_arrival IS NULL OR original_scheduled_departure IS NULL OR original_scheduled_arrival >= original_scheduled_departure),
    status varchar(20) NOT NULL CHECK (status IN ('SCHEDULED','CHECK_IN','BOARDING','DELAYED','DEPARTED','EN_ROUTE','LANDED','ARRIVED','CANCELLED','DIVERTED','UNKNOWN')),
    departure_terminal varchar(40), departure_gate varchar(40),
    arrival_terminal varchar(40), arrival_gate varchar(40),
    booking_reference varchar(32), electronic_ticket_number varchar(40),
    price_paid_amount numeric(19,2), price_paid_currency varchar(3), price_paid_amount_usd numeric(19,2),
    CHECK ((price_paid_amount IS NULL AND price_paid_currency IS NULL AND price_paid_amount_usd IS NULL)
        OR (price_paid_amount IS NOT NULL AND price_paid_currency IS NOT NULL AND price_paid_currency ~ '^[A-Z]{3}$'
        AND price_paid_amount >= 0 AND price_paid_amount <> 'NaN'::numeric
        AND (price_paid_amount_usd IS NULL OR (price_paid_amount_usd >= 0 AND price_paid_amount_usd <> 'NaN'::numeric))
        AND (price_paid_currency <> 'USD' OR (price_paid_amount_usd IS NOT NULL AND price_paid_amount_usd = price_paid_amount)))),
    ticket_total_amount numeric(19,2), ticket_total_currency varchar(3), ticket_total_amount_usd numeric(19,2),
    CHECK ((ticket_total_amount IS NULL AND ticket_total_currency IS NULL AND ticket_total_amount_usd IS NULL)
        OR (ticket_total_amount IS NOT NULL AND ticket_total_currency IS NOT NULL AND ticket_total_currency ~ '^[A-Z]{3}$'
        AND ticket_total_amount >= 0 AND ticket_total_amount <> 'NaN'::numeric
        AND (ticket_total_amount_usd IS NULL OR (ticket_total_amount_usd >= 0 AND ticket_total_amount_usd <> 'NaN'::numeric))
        AND (ticket_total_currency <> 'USD' OR (ticket_total_amount_usd IS NOT NULL AND ticket_total_amount_usd = ticket_total_amount)))),
    baggage varchar(500), seat varchar(20),
    connection_protection varchar(20) NOT NULL CHECK (connection_protection IN ('SAME_TICKET','SEPARATE_TICKETS','UNKNOWN')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at)
);
CREATE INDEX flights_leg_date_id_idx ON flights.flight_segments(trip_id, trip_leg_id, flight_date, created_at, id);

CREATE TABLE flights.flight_passengers (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL,
    flight_id uuid NOT NULL,
    passenger_id uuid NOT NULL,
    FOREIGN KEY (trip_id, flight_id) REFERENCES flights.flight_segments(trip_id, id) ON DELETE RESTRICT,
    FOREIGN KEY (trip_id, passenger_id) REFERENCES trips.passengers(trip_id, id) ON DELETE RESTRICT,
    UNIQUE (flight_id, passenger_id),
    seat varchar(20), baggage varchar(500), electronic_ticket_number varchar(40),
    price_paid_amount numeric(19,2), price_paid_currency varchar(3), price_paid_amount_usd numeric(19,2),
    CHECK ((price_paid_amount IS NULL AND price_paid_currency IS NULL AND price_paid_amount_usd IS NULL)
        OR (price_paid_amount IS NOT NULL AND price_paid_currency IS NOT NULL AND price_paid_currency ~ '^[A-Z]{3}$'
        AND price_paid_amount >= 0 AND price_paid_amount <> 'NaN'::numeric
        AND (price_paid_amount_usd IS NULL OR (price_paid_amount_usd >= 0 AND price_paid_amount_usd <> 'NaN'::numeric))
        AND (price_paid_currency <> 'USD' OR (price_paid_amount_usd IS NOT NULL AND price_paid_amount_usd = price_paid_amount))))
);
CREATE INDEX flight_passengers_trip_passenger_idx ON flights.flight_passengers(trip_id, passenger_id);

CREATE TABLE flights.flight_history (
    id uuid PRIMARY KEY,
    flight_id uuid NOT NULL REFERENCES flights.flight_segments(id) ON DELETE RESTRICT,
    revision bigint NOT NULL CHECK (revision >= 0),
    field_name varchar(80) NOT NULL CHECK (field_name IN ('flightNumber','status','departureTerminal','departureGate','arrivalTerminal','arrivalGate')),
    previous_value varchar(80), new_value varchar(80),
    source varchar(80) NOT NULL CHECK (source = 'MANUAL'),
    recorded_at timestamptz NOT NULL,
    UNIQUE (flight_id, revision, field_name),
    CHECK (previous_value IS DISTINCT FROM new_value)
);
CREATE INDEX flight_history_revision_idx ON flights.flight_history(flight_id, revision, field_name, id);
