package pe.edu.pucp.paqrap.planner.sa;

public record AnnealingConfig(double initialTemperature, double minimumTemperature,
                              double coolingFactor, int iterationsPerTemperature,
                              int maximumIterations, int maximumIterationsWithoutImprovement) {
    public AnnealingConfig {
        if (initialTemperature <= 0 || minimumTemperature <= 0 || minimumTemperature >= initialTemperature) {
            throw new IllegalArgumentException("Temperatures must be positive and Tmin < T0");
        }
        if (coolingFactor < 0.80 || coolingFactor > 0.99) {
            throw new IllegalArgumentException("Cooling factor must be within [0.80, 0.99]");
        }
        if (iterationsPerTemperature <= 0 || maximumIterations <= 0 || maximumIterationsWithoutImprovement <= 0) {
            throw new IllegalArgumentException("Iteration limits must be positive");
        }
    }
}
