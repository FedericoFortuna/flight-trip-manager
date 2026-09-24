-- Preserve existing UUIDs and data. Legacy entries remain unowned until explicitly reconciled.
ALTER TABLE catalog.locations ADD COLUMN last_synced_at timestamptz;

ALTER TABLE catalog.airports
    ADD COLUMN source varchar(64),
    ADD COLUMN external_id varchar(100),
    ADD COLUMN source_observed_at timestamptz,
    ADD CONSTRAINT airports_source_identity UNIQUE (source, external_id),
    ADD CONSTRAINT airports_source_metadata CHECK (
        (source IS NULL AND external_id IS NULL AND source_observed_at IS NULL)
        OR
        (source IS NOT NULL AND external_id IS NOT NULL AND source_observed_at IS NOT NULL
            AND last_synced_at IS NOT NULL
            AND source ~ '^[a-z0-9][a-z0-9._-]{0,63}$'
            AND external_id = btrim(external_id) AND external_id !~ '^[[:space:]]*$'
            AND external_id !~ '[[:cntrl:]]'
            AND source_observed_at >= timestamptz '1970-01-01T00:00:00Z'
            AND source_observed_at <= last_synced_at));

ALTER TABLE catalog.airlines
    ADD COLUMN source varchar(64),
    ADD COLUMN external_id varchar(100),
    ADD COLUMN source_observed_at timestamptz,
    ADD CONSTRAINT airlines_source_identity UNIQUE (source, external_id),
    ADD CONSTRAINT airlines_source_metadata CHECK (
        (source IS NULL AND external_id IS NULL AND source_observed_at IS NULL)
        OR
        (source IS NOT NULL AND external_id IS NOT NULL AND source_observed_at IS NOT NULL
            AND last_synced_at IS NOT NULL
            AND source ~ '^[a-z0-9][a-z0-9._-]{0,63}$'
            AND external_id = btrim(external_id) AND external_id !~ '^[[:space:]]*$'
            AND external_id !~ '[[:cntrl:]]'
            AND source_observed_at >= timestamptz '1970-01-01T00:00:00Z'
            AND source_observed_at <= last_synced_at));

ALTER TABLE catalog.locations
    ADD COLUMN source varchar(64),
    ADD COLUMN external_id varchar(100),
    ADD COLUMN source_observed_at timestamptz,
    ADD CONSTRAINT locations_source_identity UNIQUE (source, external_id),
    ADD CONSTRAINT locations_source_metadata CHECK (
        (source IS NULL AND external_id IS NULL AND source_observed_at IS NULL)
        OR
        (source IS NOT NULL AND external_id IS NOT NULL AND source_observed_at IS NOT NULL
            AND last_synced_at IS NOT NULL
            AND source ~ '^[a-z0-9][a-z0-9._-]{0,63}$'
            AND external_id = btrim(external_id) AND external_id !~ '^[[:space:]]*$'
            AND external_id !~ '[[:cntrl:]]'
            AND source_observed_at >= timestamptz '1970-01-01T00:00:00Z'
            AND source_observed_at <= last_synced_at));
