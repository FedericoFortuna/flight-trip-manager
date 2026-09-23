CREATE SCHEMA catalog;
CREATE SCHEMA trips;
CREATE SCHEMA flights;
CREATE SCHEMA flightsearch;
CREATE SCHEMA tracking;
CREATE SCHEMA connections;
CREATE SCHEMA budgets;

-- shared contains cross-cutting Java types, not business tables.
-- Flyway's metadata remains in public.flyway_schema_history.
