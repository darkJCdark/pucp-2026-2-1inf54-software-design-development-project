package pe.edu.pucp.paqrap.planner.domain;

import java.util.Objects;

/** Central warehouses have unlimited stock; intermediate warehouses have finite stock. */
public final class Warehouse {
    private final String id;
    private final Location location;
    private final boolean central;
    private int availableStock;

    private Warehouse(String id, Location location, boolean central, int availableStock) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.location = Objects.requireNonNull(location, "location is required");
        this.central = central;
        this.availableStock = availableStock;
    }

    public static Warehouse central(String id, Location location) {
        return new Warehouse(id, location, true, Integer.MAX_VALUE);
    }

    public static Warehouse intermediate(String id, Location location, int availableStock) {
        if (availableStock < 0 || availableStock > 1_000) {
            throw new IllegalArgumentException("Intermediate stock must be between 0 and 1,000");
        }
        return new Warehouse(id, location, false, availableStock);
    }

    public String id() { return id; }
    public Location location() { return location; }
    public boolean isCentral() { return central; }
    public int availableStock() { return availableStock; }
    public boolean hasStockFor(int packages) { return central || availableStock >= packages; }

    public void dispatch(int packages) {
        if (packages <= 0 || !hasStockFor(packages)) {
            throw new IllegalArgumentException("Insufficient warehouse stock");
        }
        if (!central) {
            availableStock -= packages;
        }
    }
}
