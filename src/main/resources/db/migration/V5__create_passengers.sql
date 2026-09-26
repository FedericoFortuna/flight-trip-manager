CREATE TABLE trips.passengers (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trips.trips(id) ON DELETE RESTRICT,
    first_name varchar(100) NOT NULL CHECK (
        first_name = btrim(first_name) AND first_name !~ '^[[:space:]]*$' AND first_name !~ '[[:cntrl:]]'),
    last_name varchar(100) NOT NULL CHECK (
        last_name = btrim(last_name) AND last_name !~ '^[[:space:]]*$' AND last_name !~ '[[:cntrl:]]'),
    notes varchar(2000) CHECK (translate(notes, E'\n\r\t', '') !~ '[[:cntrl:]]'),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL CHECK (updated_at >= created_at)
);
CREATE INDEX passengers_trip_created_id_idx ON trips.passengers (trip_id, created_at, id);
