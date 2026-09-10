package pe.edu.pucp.paqrap.planner.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** Immutable planned-maintenance lookup for a simulation. */
public final class MaintenanceCalendar {
    private final ZoneId zoneId;
    private final List<MaintenanceDay> entries;

    public MaintenanceCalendar(ZoneId zoneId, List<MaintenanceDay> entries) {
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId is required");
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries are required"));
    }

    public boolean isUnavailable(String vehicleId, Instant instant) {
        return entries.stream().anyMatch(entry -> entry.vehicleId().equals(vehicleId) && entry.contains(instant, zoneId));
    }

    public boolean isUnavailableDuring(String vehicleId, Instant fromInclusive, Instant toExclusive) {
        return entries.stream().anyMatch(entry -> entry.vehicleId().equals(vehicleId)
                && entry.overlaps(fromInclusive, toExclusive, zoneId));
    }
}
