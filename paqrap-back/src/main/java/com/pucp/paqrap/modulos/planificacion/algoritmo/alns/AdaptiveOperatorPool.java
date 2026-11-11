package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Selección adaptativa de operadores ALNS.
 *
 * Cada operador comienza con el mismo peso.
 * Después de cada uso, su peso se actualiza según
 * el resultado conseguido.
 *
 * Un operador que genera mejores soluciones tendrá
 * progresivamente mayor probabilidad de ser elegido.
 */
final class AdaptiveOperatorPool<T> {

    private static final double INITIAL_WEIGHT =
            1.0;

    private static final double MINIMUM_WEIGHT =
            0.05;

    private final List<Entry<T>> entries;

    private final Function<T, String> nameExtractor;

    private final double reactionFactor;

    AdaptiveOperatorPool(
            List<T> operators,
            Function<T, String> nameExtractor,
            double reactionFactor
    ) {

        Objects.requireNonNull(
                operators,
                "operators are required"
        );

        if (
                operators.isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "At least one operator is required"
            );
        }

        this.nameExtractor =
                Objects.requireNonNull(
                        nameExtractor,
                        "nameExtractor is required"
                );

        if (
                !Double.isFinite(
                        reactionFactor
                ) ||
                reactionFactor <= 0 ||
                reactionFactor > 1
        ) {
            throw new IllegalArgumentException(
                    "reactionFactor must be within (0, 1]"
            );
        }

        this.reactionFactor =
                reactionFactor;

        this.entries =
                new ArrayList<>(
                        operators.size()
                );

        for (
                T operator :
                operators
        ) {

            this.entries.add(
                    new Entry<>(
                            Objects.requireNonNull(
                                    operator,
                                    "operator is required"
                            ),
                            INITIAL_WEIGHT
                    )
            );
        }
    }

    /**
     * Roulette-wheel selection.
     *
     * La probabilidad de cada operador es:
     *
     * peso operador / suma pesos.
     */
    T select(
            RandomGenerator random
    ) {

        Objects.requireNonNull(
                random,
                "random is required"
        );

        double totalWeight =
                entries
                        .stream()
                        .mapToDouble(
                                Entry::weight
                        )
                        .sum();

        double draw =
                random.nextDouble() *
                        totalWeight;

        double accumulated =
                0.0;

        for (
                Entry<T> entry :
                entries
        ) {

            accumulated +=
                    entry.weight();

            if (
                    draw <=
                            accumulated
            ) {

                return entry.operator();
            }
        }

        /*
         * Protección frente a pequeños errores
         * de redondeo floating-point.
         */
        return entries
                .getLast()
                .operator();
    }

    /**
     * Actualización exponencial:
     *
     * w_nuevo =
     * (1-rho) * w_anterior
     * +
     * rho * score
     */
    void reward(
            T operator,
            double score
    ) {

        Objects.requireNonNull(
                operator,
                "operator is required"
        );

        if (
                !Double.isFinite(
                        score
                ) ||
                score < 0
        ) {
            throw new IllegalArgumentException(
                    "score must be finite and non-negative"
            );
        }

        for (
                int i = 0;
                i < entries.size();
                i++
        ) {

            Entry<T> entry =
                    entries.get(i);

            /*
             * Los pools mantienen exactamente las
             * instancias utilizadas por el planner.
             */
            if (
                    entry.operator() ==
                            operator
            ) {

                double updated =
                        (
                                1.0 -
                                reactionFactor
                        ) *
                                entry.weight()
                        +
                        reactionFactor *
                                score;

                updated =
                        Math.max(
                                MINIMUM_WEIGHT,
                                updated
                        );

                entries.set(
                        i,
                        new Entry<>(
                                entry.operator(),
                                updated
                        )
                );

                return;
            }
        }

        throw new IllegalArgumentException(
                "Unknown operator: " +
                        nameExtractor.apply(
                                operator
                        )
        );
    }

    /**
     * Snapshot de pesos para métricas/tests.
     */
    Map<String, Double> weights() {

        Map<String, Double> result =
                new LinkedHashMap<>();

        for (
                Entry<T> entry :
                entries
        ) {

            result.put(
                    nameExtractor.apply(
                            entry.operator()
                    ),
                    entry.weight()
            );
        }

        return Map.copyOf(
                result
        );
    }

    private record Entry<T>(
            T operator,
            double weight
    ) {
    }
}