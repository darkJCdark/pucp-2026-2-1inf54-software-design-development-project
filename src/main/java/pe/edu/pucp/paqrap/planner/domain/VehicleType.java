package pe.edu.pucp.paqrap.planner.domain;

public enum VehicleType {
    CAR(24, 40.0, 8.0),
    MOTORCYCLE(8, 25.0, 6.0),
    BICYCLE(4, 12.0, 3.0);

    private final int capacity;
    private final double speedKmPerHour;
    private final double costPerKm;

    VehicleType(int capacity, double speedKmPerHour, double costPerKm) {
        this.capacity = capacity;
        this.speedKmPerHour = speedKmPerHour;
        this.costPerKm = costPerKm;
    }

    public int capacity() { return capacity; }
    public double speedKmPerHour() { return speedKmPerHour; }
    public double costPerKm() { return costPerKm; }
}
