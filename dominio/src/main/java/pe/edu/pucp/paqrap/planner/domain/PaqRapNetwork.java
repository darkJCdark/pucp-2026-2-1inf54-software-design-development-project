package pe.edu.pucp.paqrap.planner.domain;

import java.util.List;

/** Official warehouse positions from the latest course clarification. */
public final class PaqRapNetwork {
    public static final Location CENTRAL_LOCATION = new Location(27, 14);
    public static final Location NORTH_WEST_LOCATION = new Location(12, 38);
    public static final Location EAST_LOCATION = new Location(57, 27);

    private PaqRapNetwork() {
    }

    public static List<Warehouse> defaultWarehouses() {
        return List.of(
                Warehouse.central("CENTRAL", CENTRAL_LOCATION),
                Warehouse.intermediate("NORTH_WEST", NORTH_WEST_LOCATION, 1_000),
                Warehouse.intermediate("EAST", EAST_LOCATION, 1_000));
    }
}
