INSERT INTO catalog.airports(id, iata_code, icao_code, name, city, country, latitude, longitude, timezone, active, last_synced_at) VALUES
('00000000-0000-0000-0000-000000000001', 'AAA', 'AAAA', 'Alpha Airport', 'Example City', 'AR', -34.822222, -58.535833, 'America/Argentina/Buenos_Aires', true, '2026-09-23T12:00:00Z'),
('00000000-0000-0000-0000-000000000002', 'AAB', NULL, 'Alpha Airport', 'Other City', 'AR', NULL, NULL, 'UTC', true, NULL),
('00000000-0000-0000-0000-000000000003', 'AAC', 'AAAC', 'Closed Airport', 'Example City', 'AR', 90, 180, 'UTC', false, NULL);
INSERT INTO catalog.airlines(id, iata_code, icao_code, name, country, active, last_synced_at) VALUES
('00000000-0000-0000-0000-000000000011', 'A1', 'AAA', 'Alpha Airlines', 'AR', true, '2026-09-23T12:00:00Z'),
('00000000-0000-0000-0000-000000000012', 'A2', NULL, 'Beta Airlines', 'BR', true, NULL),
('00000000-0000-0000-0000-000000000013', 'A3', 'AAC', 'Closed Airlines', 'AR', false, NULL);
INSERT INTO catalog.locations(id, type, name, city, country) VALUES
('00000000-0000-0000-0000-000000000021', 'CITY', 'Example City', 'Example City', 'AR'),
('00000000-0000-0000-0000-000000000022', 'TRAIN_STATION', 'Central Station', 'Example City', 'AR'),
('00000000-0000-0000-0000-000000000023', 'BUS_STATION', '100%_Terminal', 'Example City', 'AR');
