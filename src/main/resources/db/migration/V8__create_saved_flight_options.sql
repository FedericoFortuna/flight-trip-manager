CREATE TABLE flightsearch.saved_flight_options (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL,
    leg_id uuid NOT NULL,
    transport_type varchar(20) NOT NULL CHECK (transport_type = 'FLIGHT'),
    FOREIGN KEY (trip_id,leg_id,transport_type)
        REFERENCES trips.trip_legs(trip_id,id,transport_type) ON DELETE RESTRICT ON UPDATE RESTRICT,
    slot integer NOT NULL CHECK (slot BETWEEN 1 AND 3),
    UNIQUE (trip_id,leg_id,slot),
    criteria jsonb NOT NULL CHECK (jsonb_typeof(criteria) = 'object'),
    initial_offer jsonb NOT NULL CHECK (jsonb_typeof(initial_offer) = 'object'),
    active boolean NOT NULL,
    inactive_reason varchar(40),
    CHECK ((active AND inactive_reason IS NULL) OR
        (NOT active AND inactive_reason IS NOT NULL AND inactive_reason IN ('LEG_CLOSED','ROUTE_CHANGED'))),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at)
);

CREATE TABLE flightsearch.flight_price_snapshots (
    id uuid PRIMARY KEY,
    saved_flight_option_id uuid NOT NULL REFERENCES flightsearch.saved_flight_options(id) ON DELETE RESTRICT,
    source_search_id uuid NOT NULL,
    provider_offer_id varchar(200) NOT NULL,
    base_price numeric(23,4),
    baggage_price numeric(23,4),
    seat_price numeric(23,4),
    total_price numeric(23,4) NOT NULL,
    original_currency varchar(3) NOT NULL CHECK (original_currency ~ '^[A-Z]{3}$'),
    original_amount numeric(23,4) NOT NULL,
    amount_usd numeric(23,4),
    provider varchar(40) NOT NULL,
    observed_at timestamptz NOT NULL,
    synthetic boolean NOT NULL,
    test_mode boolean NOT NULL,
    missing_costs jsonb NOT NULL CHECK (jsonb_typeof(missing_costs) = 'array'),
    CHECK (total_price >= 0 AND total_price <> 'NaN'::numeric AND total_price = original_amount),
    CHECK (base_price IS NULL OR (base_price >= 0 AND base_price <> 'NaN'::numeric)),
    CHECK (baggage_price IS NULL OR (baggage_price >= 0 AND baggage_price <> 'NaN'::numeric)),
    CHECK (seat_price IS NULL OR (seat_price >= 0 AND seat_price <> 'NaN'::numeric)),
    CHECK (amount_usd IS NULL OR (amount_usd >= 0 AND amount_usd <> 'NaN'::numeric)),
    CHECK (original_currency <> 'USD' OR (amount_usd IS NOT NULL AND amount_usd = original_amount)),
    UNIQUE (saved_flight_option_id,source_search_id,provider_offer_id)
);
CREATE INDEX flight_price_history_idx
    ON flightsearch.flight_price_snapshots(saved_flight_option_id,observed_at DESC,id);
