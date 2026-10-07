CREATE TABLE flightsearch.search_history (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    criteria jsonb NOT NULL CHECK (jsonb_typeof(criteria) = 'object'),
    CHECK (criteria ?& ARRAY['origin','destination','departureDate','flexDays','adults'])
);
CREATE INDEX search_history_created_id_idx ON flightsearch.search_history(created_at DESC, id);
