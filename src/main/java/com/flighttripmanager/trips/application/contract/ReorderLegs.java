package com.flighttripmanager.trips.application.contract;

import java.util.List;
import java.util.UUID;
/** Empty orderedLegIds clears all manual positions. Otherwise supply the entire itinerary once. */
public record ReorderLegs(Long version, List<UUID> orderedLegIds) {}
