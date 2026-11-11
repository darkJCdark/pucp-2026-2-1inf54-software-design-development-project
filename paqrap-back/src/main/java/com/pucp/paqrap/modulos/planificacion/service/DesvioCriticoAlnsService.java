package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ContextoProgramacionRuta;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.planificacion.policy.PoliticaPlanificacion;
import com.pucp.paqrap.modulos.planificacion.policy.PrioridadPedido;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

/**
 * Construye desvíos físicamente consistentes para pedidos críticos.
 *
 * Nunca cambia el recorrido pasado:
 *
 * - termina el RoadLeg actual;
 * - utiliza el próximo nodo vial seguro;
 * - conserva carga;
 * - conserva refrigerio ya tomado;
 * - conserva id y salida de la ruta;
 * - recalcula únicamente el futuro.
 */
final class DesvioCriticoAlnsService {

    private final OperationalPlanEvaluator evaluador;

    private final PoliticaPlanificacion politica;

    private final CheckpointRutaService checkpoints;

    private final ContinuacionRutaService continuaciones;

    DesvioCriticoAlnsService(
            OperationalPlanEvaluator evaluador,
            PoliticaPlanificacion politica
    ) {

        this.evaluador =
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

        this.continuaciones =
                new ContinuacionRutaService(
                        new RouteScheduler(
                                new RoadNetwork()
                        )
                );
    }

    /**
     * Desvío utilizando el futuro actual de la ruta.
     */
    Optional<Resultado> intentar(
            Instant ahora,
            RutaVigente vigente,
            Order pedido,
            Warehouse central,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {

        CheckpointRuta checkpoint =
                checkpointValido(
                        ahora,
                        vigente,
                        pedido,
                        deadlineNanos
                );

        if (
                checkpoint == null
        ) {
            return Optional.empty();
        }

        List<RouteStop> futuroOriginal =
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

        return intentarDesdeCheckpoint(
                ahora,
                vigente,
                checkpoint,
                pedido,
                futuroOriginal,
                central,
                bloqueos,
                deadlineNanos
        );
    }

    /**
     * Variante utilizada por UrgencyRemoval.
     *
     * Permite probar un futuro donde previamente se
     * retiró una entrega de menor prioridad.
     */
    Optional<Resultado> intentarConFuturo(
            Instant ahora,
            RutaVigente vigente,
            Order pedido,
            List<RouteStop> futuroBase,
            Warehouse central,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {

        Objects.requireNonNull(
                futuroBase,
                "futuroBase es requerido"
        );

        CheckpointRuta checkpoint =
                checkpointValido(
                        ahora,
                        vigente,
                        pedido,
                        deadlineNanos
                );

        if (
                checkpoint == null
        ) {
            return Optional.empty();
        }

        return intentarDesdeCheckpoint(
                ahora,
                vigente,
                checkpoint,
                pedido,
                new ArrayList<>(
                        futuroBase
                ),
                central,
                bloqueos,
                deadlineNanos
        );
    }

    private CheckpointRuta checkpointValido(
            Instant ahora,
            RutaVigente vigente,
            Order pedido,
            long deadlineNanos
    ) {

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        Objects.requireNonNull(
                vigente,
                "vigente es requerida"
        );

        Objects.requireNonNull(
                pedido,
                "pedido es requerido"
        );

        if (
                deadlineAlcanzado(
                        deadlineNanos
                )
                ||
                vigente.programada() == null
        ) {

            return null;
        }

        PrioridadPedido prioridad =
                politica
                        .prioridadDesdeCentral(
                                pedido,
                                ahora,
                                vigente.snapshotOrigen()
                        );

        if (
                !politica
                        .permiteDesvioEnTransito(
                                prioridad
                        )
        ) {

            return null;
        }

        CheckpointRuta checkpoint =
                checkpoints.crear(
                        vigente,
                        ahora
                );

        /*
         * El desvío fuerte se activa únicamente cuando
         * existe un compromiso vial actual.
         */
        if (
                checkpoint.estado()
                        !=
                        EstadoRutaEnInstante.EN_TRANSITO
                &&
                checkpoint.estado()
                        !=
                        EstadoRutaEnInstante.ESPERA_VIAL
        ) {

            return null;
        }

        return checkpoint;
    }

    private Optional<Resultado> intentarDesdeCheckpoint(
            Instant ahora,
            RutaVigente vigente,
            CheckpointRuta checkpoint,
            Order pedido,
            List<RouteStop> futuroBase,
            Warehouse central,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {

        Objects.requireNonNull(
                central,
                "central es requerido"
        );

        Objects.requireNonNull(
                bloqueos,
                "bloqueos son requeridos"
        );

        Resultado mejor =
                null;

        /*
         * =================================================
         * OPCIÓN A
         *
         * Utilizar la carga actual.
         * =================================================
         */

        List<RouteStop> directo =
                new ArrayList<>();

        directo.add(
                new DeliveryStop(
                        pedido,
                        pedido.packages()
                )
        );

        directo.addAll(
                futuroBase
        );

        mejor =
                elegirMejor(
                        mejor,

                        construirYValidar(
                                ahora,
                                vigente,
                                checkpoint,
                                pedido,
                                directo,
                                bloqueos,
                                deadlineNanos
                        )
                );

        if (
                deadlineAlcanzado(
                        deadlineNanos
                )
        ) {

            return Optional.ofNullable(
                    mejor
            );
        }

        /*
         * =================================================
         * OPCIÓN B
         *
         * Ir al central, cargar el pedido y desviarse.
         * =================================================
         */

        List<RouteStop> conRecarga =
                new ArrayList<>();

        conRecarga.add(
                new WarehouseVisit(
                        central,
                        pedido.packages()
                )
        );

        conRecarga.add(
                new DeliveryStop(
                        pedido,
                        pedido.packages()
                )
        );

        conRecarga.addAll(
                futuroBase
        );

        mejor =
                elegirMejor(
                        mejor,

                        construirYValidar(
                                ahora,
                                vigente,
                                checkpoint,
                                pedido,
                                conRecarga,
                                bloqueos,
                                deadlineNanos
                        )
                );

        return Optional.ofNullable(
                mejor
        );
    }

    private Resultado construirYValidar(
            Instant ahora,
            RutaVigente vigente,
            CheckpointRuta checkpoint,
            Order pedido,
            List<RouteStop> futuro,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {

        if (
                deadlineAlcanzado(
                        deadlineNanos
                )
        ) {
            return null;
        }

        List<RouteStop> futuroConRetorno =
                asegurarRetorno(
                        futuro,
                        vigente
                );

        OperationalSnapshot snapshotCheckpoint;

        try {

            snapshotCheckpoint =
                    snapshotEnCheckpoint(
                            vigente,
                            checkpoint
                    );

        } catch (
                IllegalArgumentException exception
        ) {

            return null;
        }

        DeliveryRoute sufijo =
                crearSufijo(
                        vigente,
                        checkpoint,
                        futuroConRetorno
                );

        ContextoProgramacionRuta contexto =
                new ContextoProgramacionRuta(
                        checkpoint
                                .turnoConRefrigerioTomado()
                                .orElse(
                                        null
                                )
                );

        PlanEvaluation evaluacion =
                evaluador.evaluate(
                        OperationalPlan
                                .empty()
                                .withRoute(
                                        sufijo
                                ),

                        snapshotCheckpoint,

                        pedidosDe(
                                futuroConRetorno
                        ),

                        bloqueos,

                        Map.of(
                                sufijo.id(),
                                contexto
                        )
                );

        List<PlanViolation> violacionesReales =
                evaluacion
                        .violations()
                        .stream()

                        .filter(
                                violacion ->
                                        violacion.type()
                                                !=
                                                PlanViolationType
                                                        .PARTIAL_DELIVERY_MISMATCH
                        )

                        .toList();

        if (
                !violacionesReales.isEmpty()
        ) {
            return null;
        }

        if (
                deadlineAlcanzado(
                        deadlineNanos
                )
        ) {
            return null;
        }

        ContinuacionRutaService.Resultado continuacion;

        try {

            continuacion =
                    continuaciones.reprogramar(
                            vigente,
                            ahora,
                            futuroConRetorno,
                            snapshotCheckpoint,
                            bloqueos
                    );

        } catch (
                IllegalArgumentException
                |
                IllegalStateException exception
        ) {

            return null;
        }

        Instant llegadaPedido =
                llegadaDe(
                        continuacion
                                .programada()
                                .scheduledStops(),
                        pedido.id()
                );

        if (
                llegadaPedido == null
                ||
                llegadaPedido.isAfter(
                        pedido.deadline()
                )
        ) {

            return null;
        }

        double costoAnterior =
                vigente
                        .programada()
                        .totalCost();

        double deltaCosto =
                continuacion
                        .programada()
                        .totalCost()
                -
                costoAnterior;

        RutaVigente nueva =
                new RutaVigente(
                        continuacion.ruta(),
                        vigente.snapshotOrigen(),
                        continuacion.programada(),
                        List.of()
                );

        return new Resultado(
                nueva,
                deltaCosto,
                llegadaPedido,
                checkpoint
        );
    }

    private List<RouteStop> asegurarRetorno(
            List<RouteStop> futuro,
            RutaVigente vigente
    ) {

        List<RouteStop> resultado =
                new ArrayList<>(
                        futuro
                );

        if (
                !resultado.isEmpty()
                &&
                resultado.getLast()
                        instanceof
                        WarehouseVisit
        ) {

            return resultado;
        }

        Warehouse central =
                vigente
                        .snapshotOrigen()
                        .inventory()
                        .warehouses()
                        .stream()

                        .filter(
                                Warehouse::isCentral
                        )

                        .findFirst()

                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "El snapshot requiere un almacén central"
                                        )
                        );

        resultado.add(
                new WarehouseVisit(
                        central,
                        0
                )
        );

        return resultado;
    }

    private DeliveryRoute crearSufijo(
            RutaVigente vigente,
            CheckpointRuta checkpoint,
            List<RouteStop> futuro
    ) {

        DeliveryRoute ruta =
                DeliveryRoute
                        .replanFromCurrentLocation(
                                "CHECKPOINT-"
                                        +
                                        vigente
                                                .ruta()
                                                .id(),

                                vigente
                                        .ruta()
                                        .vehicle(),

                                checkpoint
                                        .nodoSeguro(),

                                checkpoint
                                        .carga(),

                                checkpoint
                                        .disponibleDesde()
                        );

        for (
                RouteStop parada :
                futuro
        ) {

            ruta =
                    ruta.withAppendedStop(
                            parada
                    );
        }

        return ruta;
    }

    private OperationalSnapshot snapshotEnCheckpoint(
            RutaVigente vigente,
            CheckpointRuta checkpoint
    ) {

        OperationalSnapshot origen =
                vigente.snapshotOrigen();

        InventorySnapshot inventario =
                inventarioTrasPrefijo(
                        origen.inventory(),
                        checkpoint.prefijoFijo()
                );

        VehicleOperationalState estado =
                new VehicleOperationalState(
                        vigente
                                .ruta()
                                .vehicle(),

                        VehicleStatus.IN_ROUTE,

                        checkpoint.nodoSeguro(),

                        checkpoint.carga(),

                        checkpoint.disponibleDesde()
                );

        return new OperationalSnapshot(
                checkpoint.disponibleDesde(),

                origen.fleetProfile(),

                inventario,

                Map.of(
                        vigente.vehiculoId(),
                        estado
                ),

                origen.maintenanceCalendar(),

                origen.shiftSchedule(),

                origen.breakdowns()
        );
    }

    private InventorySnapshot inventarioTrasPrefijo(
            InventorySnapshot inventarioOriginal,
            List<ScheduledRouteStop> prefijo
    ) {

        InventorySnapshot inventario =
                inventarioOriginal;

        for (
                ScheduledRouteStop parada :
                prefijo
        ) {

            if (
                    !(parada.stop()
                            instanceof
                            WarehouseVisit visita)
            ) {
                continue;
            }

            if (
                    visita.pickupPackages()
                            <=
                            0
                    ||
                    visita
                            .warehouse()
                            .isCentral()
            ) {

                continue;
            }

            if (
                    !inventario
                            .hasStockFor(
                                    visita
                                            .warehouse()
                                            .id(),
                                    visita.pickupPackages()
                            )
            ) {

                throw new IllegalArgumentException(
                        "El prefijo consume inventario no disponible"
                );
            }

            inventario =
                    inventario.withdraw(
                            visita
                                    .warehouse()
                                    .id(),
                            visita.pickupPackages()
                    );
        }

        return inventario;
    }

    private Collection<Order> pedidosDe(
            List<RouteStop> paradas
    ) {

        Map<String, Order> pedidos =
                new LinkedHashMap<>();

        for (
                RouteStop parada :
                paradas
        ) {

            if (
                    parada instanceof
                            DeliveryStop entrega
            ) {

                pedidos.putIfAbsent(
                        entrega
                                .order()
                                .id(),

                        entrega.order()
                );
            }
        }

        return pedidos.values();
    }

    private Instant llegadaDe(
            List<ScheduledRouteStop> paradas,
            String pedidoId
    ) {

        return paradas
                .stream()

                .filter(
                        parada ->
                                parada.stop()
                                        instanceof
                                        DeliveryStop
                )

                .filter(
                        parada ->
                                (
                                        (
                                                DeliveryStop
                                        ) parada.stop()
                                )
                                        .order()
                                        .id()
                                        .equals(
                                                pedidoId
                                        )
                )

                .map(
                        ScheduledRouteStop::arrivedAt
                )

                .findFirst()

                .orElse(
                        null
                );
    }

    private Resultado elegirMejor(
            Resultado actual,
            Resultado candidato
    ) {

        if (
                candidato == null
        ) {
            return actual;
        }

        if (
                actual == null
        ) {
            return candidato;
        }

        int comparacionLlegada =
                candidato
                        .llegadaPedido()
                        .compareTo(
                                actual
                                        .llegadaPedido()
                        );

        if (
                comparacionLlegada < 0
        ) {
            return candidato;
        }

        if (
                comparacionLlegada > 0
        ) {
            return actual;
        }

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
            double deltaCosto,
            Instant llegadaPedido,
            CheckpointRuta checkpoint
    ) {

        Resultado {

            Objects.requireNonNull(
                    ruta,
                    "ruta es requerida"
            );

            Objects.requireNonNull(
                    llegadaPedido,
                    "llegadaPedido es requerida"
            );

            Objects.requireNonNull(
                    checkpoint,
                    "checkpoint es requerido"
            );
        }
    }
}