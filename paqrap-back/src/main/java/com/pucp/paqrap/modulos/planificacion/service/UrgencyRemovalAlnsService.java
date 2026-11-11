package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.planificacion.policy.PoliticaPlanificacion;
import com.pucp.paqrap.modulos.planificacion.policy.PrioridadPedido;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Redistribución dirigida por urgencia.
 *
 * Para salvar un pedido CRÍTICO puede retirar de una ruta
 * una entrega futura menos prioritaria y devolverla al pool
 * de pendientes.
 *
 * REGLA FÍSICA:
 *
 * solo se desplazan entregas cuyos paquetes todavía NO
 * fueron cargados.
 *
 * Para reconocerlas exigimos:
 *
 * WarehouseVisit(+q)
 * DeliveryStop(q)
 *
 * dentro de la parte futura de la ruta.
 *
 * Ambos elementos se retiran juntos.
 */
final class UrgencyRemovalAlnsService {

    private static final int MAX_CANDIDATOS_POR_RUTA =
            3;

    private final PoliticaPlanificacion politica;

    private final CheckpointRutaService checkpoints;

    private final DesvioCriticoAlnsService desvios;

    UrgencyRemovalAlnsService(
            OperationalPlanEvaluator evaluador,
            PoliticaPlanificacion politica
    ) {

        Objects.requireNonNull(
                evaluador,
                "evaluador es requerido"
        );

        this.politica =
                Objects.requireNonNull(
                        politica,
                        "politica es requerida"
                );

        this.checkpoints =
                new CheckpointRutaService();

        this.desvios =
                new DesvioCriticoAlnsService(
                        evaluador,
                        politica
                );
    }

    Optional<Resultado> intentar(
            Instant ahora,
            List<RutaVigente> todasLasRutas,
            int indiceRuta,
            Order critico,
            Warehouse central,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {

        Objects.requireNonNull(
                todasLasRutas,
                "todasLasRutas son requeridas"
        );

        Objects.requireNonNull(
                critico,
                "critico es requerido"
        );

        if (
                indiceRuta < 0
                ||
                indiceRuta >=
                        todasLasRutas.size()
        ) {

            throw new IllegalArgumentException(
                    "indiceRuta fuera de rango"
            );
        }

        if (
                deadlineAlcanzado(
                        deadlineNanos
                )
        ) {
            return Optional.empty();
        }

        RutaVigente vigente =
                todasLasRutas.get(
                        indiceRuta
                );

        if (
                vigente.programada() == null
        ) {
            return Optional.empty();
        }

        PrioridadPedido prioridadCritico =
                politica
                        .prioridadDesdeCentral(
                                critico,
                                ahora,
                                vigente.snapshotOrigen()
                        );

        if (
                !prioridadCritico.critica()
        ) {
            return Optional.empty();
        }

        CheckpointRuta checkpoint =
                checkpoints.crear(
                        vigente,
                        ahora
                );

        if (
                checkpoint.estado()
                        !=
                        EstadoRutaEnInstante.EN_TRANSITO
                &&
                checkpoint.estado()
                        !=
                        EstadoRutaEnInstante.ESPERA_VIAL
        ) {

            return Optional.empty();
        }

        List<RouteStop> futuro =
                new ArrayList<>(
                        vigente
                                .ruta()
                                .stops()
                                .subList(
                                        checkpoint
                                                .primeraParadaFutura(),

                                        vigente
                                                .ruta()
                                                .stops()
                                                .size()
                                )
                );

        List<Removible> removibles =
                buscarRemovibles(
                        ahora,
                        todasLasRutas,
                        vigente,
                        futuro
                );

        Resultado mejor =
                null;

        int limite =
                Math.min(
                        MAX_CANDIDATOS_POR_RUTA,
                        removibles.size()
                );

        for (
                int i = 0;
                i < limite;
                i++
        ) {

            if (
                    deadlineAlcanzado(
                            deadlineNanos
                    )
            ) {
                break;
            }

            Removible removible =
                    removibles.get(i);

            List<RouteStop> futuroReducido =
                    quitarParejaCargaEntrega(
                            futuro,
                            removible
                    );

            var intento =
                    desvios.intentarConFuturo(
                            ahora,
                            vigente,
                            critico,
                            futuroReducido,
                            central,
                            bloqueos,
                            deadlineNanos
                    );

            if (
                    intento.isEmpty()
            ) {
                continue;
            }

            DesvioCriticoAlnsService.Resultado desvio =
                    intento.orElseThrow();

            Resultado candidato =
                    new Resultado(
                            desvio.ruta(),
                            removible.entrega().order(),
                            removible.prioridad(),
                            desvio.deltaCosto(),
                            desvio.llegadaPedido()
                    );

            mejor =
                    elegirMejor(
                            mejor,
                            candidato
                    );
        }

        return Optional.ofNullable(
                mejor
        );
    }

    /**
     * Solo devuelve pares:
     *
     * almacén(+q)
     * entrega(q)
     *
     * completamente futuros.
     */
    private List<Removible> buscarRemovibles(
            Instant ahora,
            List<RutaVigente> todasLasRutas,
            RutaVigente vigente,
            List<RouteStop> futuro
    ) {

        List<Removible> resultado =
                new ArrayList<>();

        /*
         * i empieza en 1 porque necesitamos una
         * visita de almacén inmediatamente anterior.
         */
        for (
                int i = 1;
                i < futuro.size();
                i++
        ) {

            RouteStop anterior =
                    futuro.get(
                            i - 1
                    );

            RouteStop actual =
                    futuro.get(i);

            if (
                    !(anterior
                            instanceof
                            WarehouseVisit carga)
                    ||
                    !(actual
                            instanceof
                            DeliveryStop entrega)
            ) {

                continue;
            }

            /*
             * La visita debe cargar exactamente lo
             * que se entregará.
             */
            if (
                    carga.pickupPackages()
                            !=
                            entrega.deliveredPackages()
            ) {
                continue;
            }

            /*
             * Debe representar el pedido completo.
             *
             * No movemos un fragmento parcial.
             */
            if (
                    entrega.deliveredPackages()
                            !=
                            entrega
                                    .order()
                                    .packages()
            ) {
                continue;
            }

            /*
             * El pedido no debe estar repartido entre
             * distintas rutas activas.
             */
            if (
                    !asignacionUnica(
                            entrega,
                            todasLasRutas
                    )
            ) {
                continue;
            }

            PrioridadPedido prioridad =
                    politica
                            .prioridadDesdeCentral(
                                    entrega.order(),
                                    ahora,
                                    vigente.snapshotOrigen()
                            );

            /*
             * Nunca sacrificamos otro pedido crítico
             * para salvar el actual.
             */
            if (
                    prioridad.critica()
            ) {
                continue;
            }

            resultado.add(
                    new Removible(
                            i - 1,
                            i,
                            carga,
                            entrega,
                            prioridad
                    )
            );
        }

        /*
         * Primero intentamos desplazar pedidos de menor
         * prioridad.
         *
         * NORMAL > ALTA > URGENTE para removal.
         */
        resultado.sort(
                Comparator
                        .comparingInt(
                                (
                                        Removible removible
                                ) ->
                                        removible
                                                .prioridad()
                                                .nivel()
                                                .ordinal()
                        )
                        .reversed()

                        /*
                         * Mayor slack = mejor candidato
                         * para ser desplazado.
                         */
                        .thenComparing(
                                (
                                        Removible removible
                                ) ->
                                        removible
                                                .prioridad()
                                                .slack(),

                                Comparator.reverseOrder()
                        )

                        .thenComparing(
                                removible ->
                                        removible
                                                .entrega()
                                                .order()
                                                .id()
                        )
        );

        return resultado;
    }

    private boolean asignacionUnica(
            DeliveryStop objetivo,
            List<RutaVigente> rutas
    ) {

        int apariciones =
                0;

        int cantidad =
                0;

        for (
                RutaVigente ruta :
                rutas
        ) {

            for (
                    RouteStop parada :
                    ruta
                            .ruta()
                            .stops()
            ) {

                if (
                        parada instanceof
                                DeliveryStop entrega
                        &&
                        entrega
                                .order()
                                .id()
                                .equals(
                                        objetivo
                                                .order()
                                                .id()
                                )
                ) {

                    apariciones++;

                    cantidad +=
                            entrega.deliveredPackages();
                }
            }
        }

        return apariciones == 1
                &&
                cantidad ==
                        objetivo
                                .order()
                                .packages();
    }

    private List<RouteStop> quitarParejaCargaEntrega(
            List<RouteStop> futuro,
            Removible removible
    ) {

        List<RouteStop> resultado =
                new ArrayList<>(
                        futuro
                );

        /*
         * Quitamos primero la entrega porque su índice
         * es mayor.
         */
        resultado.remove(
                removible.indiceEntrega()
        );

        resultado.remove(
                removible.indiceCarga()
        );

        return resultado;
    }

    private Resultado elegirMejor(
            Resultado actual,
            Resultado candidato
    ) {

        if (
                actual == null
        ) {
            return candidato;
        }

        /*
         * Primero queremos salvar el crítico lo antes
         * posible.
         */
        int llegada =
                candidato
                        .llegadaCritico()
                        .compareTo(
                                actual
                                        .llegadaCritico()
                        );

        if (
                llegada < 0
        ) {
            return candidato;
        }

        if (
                llegada > 0
        ) {
            return actual;
        }

        /*
         * Empate:
         *
         * preferimos desplazar el pedido menos urgente.
         */
        int nivel =
                Integer.compare(
                        candidato
                                .prioridadDesplazado()
                                .nivel()
                                .ordinal(),

                        actual
                                .prioridadDesplazado()
                                .nivel()
                                .ordinal()
                );

        if (
                nivel > 0
        ) {
            return candidato;
        }

        if (
                nivel < 0
        ) {
            return actual;
        }

        int slack =
                candidato
                        .prioridadDesplazado()
                        .slack()
                        .compareTo(
                                actual
                                        .prioridadDesplazado()
                                        .slack()
                        );

        if (
                slack > 0
        ) {
            return candidato;
        }

        if (
                slack < 0
        ) {
            return actual;
        }

        /*
         * Último criterio:
         * costo.
         */
        return candidato.deltaCosto()
                <
                actual.deltaCosto()

                ? candidato
                : actual;
    }

    private boolean deadlineAlcanzado(
            long deadlineNanos
    ) {

        return deadlineNanos
                !=
                Long.MAX_VALUE
                &&
                System.nanoTime()
                        >=
                        deadlineNanos;
    }

    record Resultado(
            RutaVigente ruta,
            Order desplazado,
            PrioridadPedido prioridadDesplazado,
            double deltaCosto,
            Instant llegadaCritico
    ) {

        Resultado {

            Objects.requireNonNull(
                    ruta,
                    "ruta es requerida"
            );

            Objects.requireNonNull(
                    desplazado,
                    "desplazado es requerido"
            );

            Objects.requireNonNull(
                    prioridadDesplazado,
                    "prioridadDesplazado es requerida"
            );

            Objects.requireNonNull(
                    llegadaCritico,
                    "llegadaCritico es requerida"
            );
        }
    }

    private record Removible(
            int indiceCarga,
            int indiceEntrega,
            WarehouseVisit carga,
            DeliveryStop entrega,
            PrioridadPedido prioridad
    ) {
    }
}