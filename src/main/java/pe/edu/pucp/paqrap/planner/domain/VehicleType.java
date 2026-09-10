package pe.edu.pucp.paqrap.planner.domain;

public enum VehicleType {
    CAR("TA", new VehicleParameters(24, 40.0, 8.0)),
    MOTORCYCLE("TM", new VehicleParameters(8, 25.0, 6.0)),
    BICYCLE("TB", new VehicleParameters(4, 12.0, 3.0));

    private final String fleetCode;
    private final VehicleParameters defaultParameters;

    VehicleType(String fleetCode, VehicleParameters defaultParameters) {
        this.fleetCode = fleetCode;
        this.defaultParameters = defaultParameters;
    }

    public String fleetCode() { return fleetCode; }
    public VehicleParameters defaultParameters() { return defaultParameters; }

    // Compatibility accessors. Route evaluation will consume FleetProfile in phase 2.
    public int capacity() { return defaultParameters.capacity(); }
    public double speedKmPerHour() { return defaultParameters.speedKmPerHour(); }
    public double costPerKm() { return defaultParameters.costPerKm(); }
}
