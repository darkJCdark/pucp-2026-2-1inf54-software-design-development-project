package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

/**
 * Configuración inmutable de una ejecución ALNS.
 */
public record AlnsConfig(
        int initialBatchSize,
        int candidateVehicles,
        int minimumRoutesToRemove,
        int maximumRoutesToRemove,
        int maximumIterations,
        int maximumIterationsWithoutImprovement,
        double initialTemperature,
        double minimumTemperature,
        double coolingFactor,
        double reactionFactor
) {

    public AlnsConfig {
        if (initialBatchSize <= 0) {
            throw new IllegalArgumentException(
                    "initialBatchSize must be positive"
            );
        }

        if (candidateVehicles <= 0) {
            throw new IllegalArgumentException(
                    "candidateVehicles must be positive"
            );
        }

        if (
                minimumRoutesToRemove <= 0 ||
                maximumRoutesToRemove < minimumRoutesToRemove
        ) {
            throw new IllegalArgumentException(
                    "route removal limits are invalid"
            );
        }

        if (
                maximumIterations <= 0 ||
                maximumIterationsWithoutImprovement <= 0
        ) {
            throw new IllegalArgumentException(
                    "iteration limits must be positive"
            );
        }

        if (
                !Double.isFinite(initialTemperature) ||
                !Double.isFinite(minimumTemperature) ||
                initialTemperature <= 0 ||
                minimumTemperature <= 0 ||
                minimumTemperature >= initialTemperature
        ) {
            throw new IllegalArgumentException(
                    "temperatures must be finite, positive and Tmin < T0"
            );
        }

        if (
                !Double.isFinite(coolingFactor) ||
                coolingFactor < 0.80 ||
                coolingFactor >= 1.0
        ) {
            throw new IllegalArgumentException(
                    "coolingFactor must be within [0.80, 1.0)"
            );
        }

        if (
                !Double.isFinite(reactionFactor) ||
                reactionFactor <= 0 ||
                reactionFactor > 1.0
        ) {
            throw new IllegalArgumentException(
                    "reactionFactor must be within (0, 1]"
            );
        }
    }

    public static AlnsConfig defaults() {
        return new AlnsConfig(
                32,
                6,
                1,
                6,
                800,
                200,
                50.0,
                0.5,
                0.995,
                0.20
        );
    }
}