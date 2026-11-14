ALTER TABLE trips.trip_legs ADD CONSTRAINT leg_budget_trip_identity UNIQUE (trip_id,id);

CREATE TABLE budgets.budget_limits (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trips.trips(id) ON DELETE RESTRICT,
    scope varchar(20) NOT NULL CHECK (scope IN ('CATEGORY','LEG')),
    category varchar(20),
    leg_id uuid,
    amount_usd numeric(19,2) NOT NULL CHECK (amount_usd >= 0 AND amount_usd <> 'NaN'::numeric),
    CHECK ((scope = 'CATEGORY' AND leg_id IS NULL AND category IS NOT NULL
            AND category IN ('FLIGHTS','TRAINS','BUSES','CAR','OTHER_TRANSPORT'))
        OR (scope = 'LEG' AND leg_id IS NOT NULL AND category IS NULL)),
    FOREIGN KEY (trip_id,leg_id) REFERENCES trips.trip_legs(trip_id,id) ON DELETE RESTRICT,
    UNIQUE (trip_id,category),
    UNIQUE (trip_id,leg_id)
);
CREATE INDEX budget_limits_scope_idx ON budgets.budget_limits(trip_id,scope);

CREATE TABLE budgets.transport_expenses (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trips.trips(id) ON DELETE RESTRICT,
    leg_id uuid,
    transport_type varchar(20) NOT NULL CHECK (transport_type IN ('FLIGHT','TRAIN','BUS','CAR','OTHER')),
    category varchar(20) NOT NULL CHECK (category IN ('FLIGHTS','TRAINS','BUSES','CAR','OTHER_TRANSPORT')),
    CHECK (transport_type = CASE category WHEN 'FLIGHTS' THEN 'FLIGHT' WHEN 'TRAINS' THEN 'TRAIN'
        WHEN 'BUSES' THEN 'BUS' WHEN 'CAR' THEN 'CAR' ELSE 'OTHER' END),
    FOREIGN KEY (trip_id,leg_id,transport_type)
        REFERENCES trips.trip_legs(trip_id,id,transport_type) ON DELETE RESTRICT ON UPDATE RESTRICT,
    description varchar(200) NOT NULL CHECK (length(trim(description)) > 0),
    reference_key varchar(100),
    CONSTRAINT expense_reference_key_uq UNIQUE (trip_id,reference_key),
    CHECK (reference_key IS NULL OR reference_key ~ '^[A-Za-z0-9._:-]{1,100}$'),
    estimated_amount numeric(23,4), estimated_currency varchar(3),
    estimated_amount_usd numeric(23,4), estimated_conversion_note varchar(200),
    actual_amount numeric(23,4), actual_currency varchar(3),
    actual_amount_usd numeric(23,4), actual_conversion_note varchar(200),
    CHECK (estimated_amount IS NOT NULL OR actual_amount IS NOT NULL),
    CHECK (
        (estimated_amount IS NULL AND estimated_currency IS NULL AND estimated_amount_usd IS NULL AND estimated_conversion_note IS NULL)
        OR (estimated_amount IS NOT NULL AND estimated_amount >= 0 AND estimated_amount <> 'NaN'::numeric
            AND estimated_currency IS NOT NULL AND estimated_currency ~ '^[A-Z]{3}$'
            AND (estimated_amount_usd IS NULL OR (estimated_amount_usd >= 0 AND estimated_amount_usd <> 'NaN'::numeric))
            AND (estimated_currency <> 'USD' OR (estimated_amount_usd IS NOT NULL AND estimated_amount_usd = estimated_amount))
            AND (estimated_currency = 'USD' OR estimated_amount_usd IS NULL OR
                (estimated_conversion_note IS NOT NULL AND length(trim(estimated_conversion_note)) > 0)))
    ),

    CHECK (
        (actual_amount IS NULL AND actual_currency IS NULL AND actual_amount_usd IS NULL AND actual_conversion_note IS NULL)
        OR (actual_amount IS NOT NULL AND actual_amount >= 0 AND actual_amount <> 'NaN'::numeric
            AND actual_currency IS NOT NULL AND actual_currency ~ '^[A-Z]{3}$'
            AND (actual_amount_usd IS NULL OR (actual_amount_usd >= 0 AND actual_amount_usd <> 'NaN'::numeric))
            AND (actual_currency <> 'USD' OR (actual_amount_usd IS NOT NULL AND actual_amount_usd = actual_amount))
            AND (actual_currency = 'USD' OR actual_amount_usd IS NULL OR
                (actual_conversion_note IS NOT NULL AND length(trim(actual_conversion_note)) > 0)))
    ),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at)
);
CREATE INDEX transport_expenses_page_idx ON budgets.transport_expenses(trip_id,created_at DESC,id);
CREATE INDEX transport_expenses_leg_idx ON budgets.transport_expenses(trip_id,leg_id);
