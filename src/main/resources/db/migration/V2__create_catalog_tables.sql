CREATE TABLE catalog.airports (
    id uuid PRIMARY KEY,
    iata_code varchar(3) NOT NULL UNIQUE CHECK (iata_code ~ '^[A-Z]{3}$'),
    icao_code varchar(4) UNIQUE CHECK (icao_code ~ '^[A-Z]{4}$'),
    name varchar(200) NOT NULL CHECK (name = btrim(name) AND name !~ '^[[:space:]]*$'),
    city varchar(120) NOT NULL CHECK (city = btrim(city) AND city !~ '^[[:space:]]*$'),
    country varchar(2) NOT NULL CHECK (country ~ '^[A-Z]{2}$'),
    latitude numeric(9,6),
    longitude numeric(9,6),
    timezone varchar(100) NOT NULL CHECK (timezone = btrim(timezone) AND timezone !~ '^[[:space:]]*$'),
    active boolean NOT NULL DEFAULT true,
    last_synced_at timestamptz,
    CONSTRAINT airport_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL) OR
        (latitude IS NOT NULL AND longitude IS NOT NULL
            AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))
);
CREATE INDEX airports_active_name_id_idx ON catalog.airports (active, name, id);

CREATE TABLE catalog.airlines (
    id uuid PRIMARY KEY,
    iata_code varchar(2) NOT NULL UNIQUE CHECK (iata_code ~ '^[A-Z0-9]{2}$'),
    icao_code varchar(3) UNIQUE CHECK (icao_code ~ '^[A-Z]{3}$'),
    name varchar(200) NOT NULL CHECK (name = btrim(name) AND name !~ '^[[:space:]]*$'),
    country varchar(2) NOT NULL CHECK (country ~ '^[A-Z]{2}$'),
    active boolean NOT NULL DEFAULT true,
    last_synced_at timestamptz
);
CREATE INDEX airlines_active_name_id_idx ON catalog.airlines (active, name, id);

CREATE TABLE catalog.locations (
    id uuid PRIMARY KEY,
    type varchar(20) NOT NULL CHECK (type IN ('CITY', 'TRAIN_STATION', 'BUS_STATION')),
    name varchar(200) NOT NULL CHECK (name = btrim(name) AND name !~ '^[[:space:]]*$'),
    city varchar(120) NOT NULL CHECK (city = btrim(city) AND city !~ '^[[:space:]]*$'),
    country varchar(2) NOT NULL CHECK (country ~ '^[A-Z]{2}$')
);
CREATE UNIQUE INDEX locations_identity_idx ON catalog.locations (type, lower(name), lower(city), country);
CREATE INDEX locations_name_id_idx ON catalog.locations (name, id);
