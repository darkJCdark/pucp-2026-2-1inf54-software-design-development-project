package pe.edu.pucp.paqrap.planner.domain;

/** Runtime parameters for one vehicle type, frozen inside a planning iteration. */
public record VehicleParameters(int capacity, double speedKmPerHour, double costPerKm) {
    public VehicleParameters {
        if (capacity <= 0 || !Double.isFinite(speedKmPerHour) || !Double.isFinite(costPerKm) || speedKmPerHour <= 0 || costPerKm < 0) {
            throw new IllegalArgumentException("Capacity and speed must be positive; cost cannot be negative");
        }
    }
}
