package com.pucp.paqrap.modulos.planificacion.algoritmo.common;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Comparador compartido para SA y ALNS.
 *
 * Esta clase contiene la semántica del objetivo.
 *
 * No pertenece específicamente a ninguna metaheurística.
 *
 * De esta forma:
 *
 *     SA
 *       \
 *        -> CriterioObjetivoPlanificacion
 *       /
 *     ALNS
 *
 * pueden compararse usando exactamente las mismas reglas.
 */
public final class CriterioObjetivoPlanificacion {

    private final ObjetivoPlanificacion objetivo;

    public CriterioObjetivoPlanificacion(
            ObjetivoPlanificacion objetivo
    ) {

        this.objetivo =
                Objects.requireNonNull(
                        objetivo,
                        "objetivo is required"
                );
    }

    public ObjetivoPlanificacion objetivo() {

        return objetivo;
    }

    /*
     * =====================================================
     * CALIDAD
     * =====================================================
     */

    public Calidad calidad(
            ResultadoPlanificacion resultado
    ) {

        Objects.requireNonNull(
                resultado,
                "resultado is required"
        );

        return new Calidad(
                resultado
                        .noAtendidos()
                        .size(),

                resultado.costoTotal()
        );
    }

    /*
     * =====================================================
     * COMPARACIÓN
     * =====================================================
     *
     * Convención:
     *
     * < 0  -> candidato es mejor
     * = 0  -> equivalentes para el objetivo
     * > 0  -> candidato es peor
     *
     * Es la misma convención de Comparator.
     */

    public int comparar(
            ResultadoPlanificacion candidato,
            ResultadoPlanificacion referencia
    ) {

        Objects.requireNonNull(
                candidato,
                "candidato is required"
        );

        Objects.requireNonNull(
                referencia,
                "referencia is required"
        );

        Calidad calidadCandidato =
                calidad(
                        candidato
                );

        Calidad calidadReferencia =
                calidad(
                        referencia
                );

        /*
         * =================================================
         * 1. NIVEL DE SERVICIO
         * =================================================
         */

        int comparacionServicio =
                Integer.compare(
                        calidadCandidato
                                .noAtendidos(),

                        calidadReferencia
                                .noAtendidos()
                );

        if (
                comparacionServicio != 0
        ) {

            return comparacionServicio;
        }

        /*
         * =================================================
         * 2. COSTO
         * =================================================
         */

        if (
                objetivo
                        ==
                        ObjetivoPlanificacion.SERVICE_MAX
        ) {

            /*
             * El experimento SERVICE_MAX quiere conocer
             * el techo de servicio.
             *
             * Si ambas soluciones atienden exactamente
             * la misma cantidad de pedidos, el dinero no
             * afecta el objetivo.
             */
            return 0;
        }

        return Double.compare(
                calidadCandidato.costo(),
                calidadReferencia.costo()
        );
    }

    public boolean esMejor(
            ResultadoPlanificacion candidato,
            ResultadoPlanificacion referencia
    ) {

        return comparar(
                candidato,
                referencia
        )
                <
                0;
    }

    public boolean esEquivalente(
            ResultadoPlanificacion first,
            ResultadoPlanificacion second
    ) {

        return comparar(
                first,
                second
        )
                ==
                0;
    }

    /*
     * =====================================================
     * ACEPTACIÓN METAHEURÍSTICA
     * =====================================================
     *
     * Esta parte puede ser compartida por SA y ALNS.
     *
     * Regla fundamental:
     *
     *     NUNCA sacrificamos nivel de servicio para
     *     reducir costo.
     *
     * Por tanto una solución que deja más pedidos sin
     * atender no entra ni siquiera por Metrópolis.
     */

    public boolean aceptar(
            ResultadoPlanificacion candidato,
            ResultadoPlanificacion actual,
            double temperatura,
            RandomGenerator random
    ) {

        Objects.requireNonNull(
                candidato,
                "candidato is required"
        );

        Objects.requireNonNull(
                actual,
                "actual is required"
        );

        Objects.requireNonNull(
                random,
                "random is required"
        );

        if (
                Double.isNaN(
                        temperatura
                )
                ||
                temperatura <= 0.0
        ) {

            throw new IllegalArgumentException(
                    "temperatura must be positive"
            );
        }

        Calidad calidadCandidato =
                calidad(
                        candidato
                );

        Calidad calidadActual =
                calidad(
                        actual
                );

        /*
         * =================================================
         * MEJOR SERVICIO
         * =================================================
         */

        if (
                calidadCandidato.noAtendidos()
                        <
                        calidadActual.noAtendidos()
        ) {

            return true;
        }

        /*
         * =================================================
         * PEOR SERVICIO
         * =================================================
         *
         * Ni SA ni ALNS pueden aceptar una solución
         * que pierda pedidos para ahorrar dinero.
         */

        if (
                calidadCandidato.noAtendidos()
                        >
                        calidadActual.noAtendidos()
        ) {

            return false;
        }

        /*
         * =================================================
         * MISMO SERVICIO
         * =================================================
         */

        if (
                objetivo
                        ==
                        ObjetivoPlanificacion.SERVICE_MAX
        ) {

            /*
             * Todo el espacio con el mismo nivel de
             * servicio es neutro.
             *
             * Esto permite que la metaheurística explore
             * rutas distintas sin introducir costo.
             */
            return true;
        }

        /*
         * SERVICE_THEN_COST:
         *
         * con el mismo servicio volvemos al comportamiento
         * tradicional de SA/ALNS sobre costo.
         */

        double deltaCosto =
                calidadCandidato.costo()
                        -
                        calidadActual.costo();

        if (
                deltaCosto <= 0.0
        ) {

            return true;
        }

        double probability =
                Math.exp(
                        -deltaCosto
                                /
                                temperatura
                );

        return random.nextDouble()
                <
                probability;
    }

    /*
     * =====================================================
     * MÉTRICAS
     * =====================================================
     */

    public record Calidad(
            int noAtendidos,
            double costo
    ) {

        public Calidad {

            if (
                    noAtendidos < 0
            ) {

                throw new IllegalArgumentException(
                        "noAtendidos cannot be negative"
                );
            }

            if (
                    Double.isNaN(
                            costo
                    )
                    ||
                    costo < 0.0
            ) {

                throw new IllegalArgumentException(
                        "costo must be non-negative"
                );
            }
        }
    }
}
