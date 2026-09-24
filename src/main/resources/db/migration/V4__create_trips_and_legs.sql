CREATE TABLE trips.trips (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL CHECK (name = btrim(name) AND name !~ '^[[:space:]]*$'),
    start_date date NOT NULL CHECK (start_date >= DATE '0001-01-01'),
    end_date date NOT NULL CHECK (end_date >= start_date AND end_date <= DATE '9999-12-31'),
    total_budget_usd numeric(19,2) CHECK (total_budget_usd >= 0 AND total_budget_usd <> 'NaN'::numeric),
    manual_status_override varchar(30) CHECK (manual_status_override IN
        ('PLANNING','PARTIALLY_BOOKED','BOOKED','UPCOMING','IN_PROGRESS','COMPLETED','CANCELLED')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at)
);
CREATE INDEX trips_created_id_idx ON trips.trips (created_at DESC, id);

CREATE TABLE trips.trip_legs (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trips.trips(id) ON DELETE RESTRICT,
    origin_airport_id uuid REFERENCES catalog.airports(id) ON DELETE RESTRICT,
    origin_location_id uuid REFERENCES catalog.locations(id) ON DELETE RESTRICT,
    destination_airport_id uuid REFERENCES catalog.airports(id) ON DELETE RESTRICT,
    destination_location_id uuid REFERENCES catalog.locations(id) ON DELETE RESTRICT,
    transport_type varchar(20) NOT NULL CHECK (transport_type IN ('FLIGHT','TRAIN','BUS','CAR','OTHER')),
    departure_date date CHECK (departure_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    departure_date_time timestamptz,
    arrival_date date CHECK (arrival_date BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
    arrival_date_time timestamptz,
    declared_status varchar(20) NOT NULL CHECK (declared_status IN
        ('PLANNED','SEARCHING','COMPARING','BOOKED','UPCOMING','IN_PROGRESS','COMPLETED','CANCELLED','DISRUPTED')),
    manual_order integer CHECK (manual_order >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at),
    CONSTRAINT leg_origin CHECK (num_nonnulls(origin_airport_id, origin_location_id) = 1),
    CONSTRAINT leg_destination CHECK (num_nonnulls(destination_airport_id, destination_location_id) = 1),
    CONSTRAINT leg_distinct_places CHECK (
        (origin_airport_id IS NULL OR destination_airport_id IS NULL OR origin_airport_id <> destination_airport_id)
        AND (origin_location_id IS NULL OR destination_location_id IS NULL OR origin_location_id <> destination_location_id)),
    CONSTRAINT leg_date_required CHECK (declared_status = 'PLANNED' OR departure_date IS NOT NULL),
    CONSTRAINT leg_date_range CHECK (arrival_date IS NULL OR (departure_date IS NOT NULL AND arrival_date >= departure_date)),
    CONSTRAINT leg_departure_precision CHECK (departure_date_time IS NULL OR
        (departure_date IS NOT NULL AND departure_date = (departure_date_time AT TIME ZONE 'UTC')::date)),
    CONSTRAINT leg_arrival_precision CHECK (arrival_date_time IS NULL OR
        (arrival_date IS NOT NULL AND arrival_date = (arrival_date_time AT TIME ZONE 'UTC')::date)),
    CONSTRAINT leg_instant_range CHECK (arrival_date_time IS NULL OR departure_date_time IS NULL OR arrival_date_time >= departure_date_time),
    CONSTRAINT leg_manual_order_unique UNIQUE (trip_id, manual_order) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX legs_trip_departure_idx ON trips.trip_legs (trip_id, departure_date, created_at, id);
