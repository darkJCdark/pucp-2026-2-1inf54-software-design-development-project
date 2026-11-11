package com.pucp.paqrap.modulos.planificacion.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.redvial.entity.Location;

/**
 * Política operacional utilizada por los planificadores.
 *
 * Aquí viven decisiones de prioridad.
 *
 * Las restricciones duras continúan viviendo en:
 *
 * - RouteScheduler
 * - OperationalPlanEvaluator
 *
 * Esto evita duplicar reglas de capacidad, SLA,
 * refrigerio, bloqueos, inventario, etc.
 */
public final class PoliticaPlanificacion {

    private static final Duration UMBRAL_URGENTE =
            Duration.ofHours(
                    1
            );

    private static final Duration UMBRAL_ALTA =
            Duration.ofHours(
                    3
            );

    /**
     * Evalúa un pedido utilizando como estimación
     * optimista el viaje desde el almacén central
     * con la unidad más rápida de la flota actual.
     */
    public PrioridadPedido prioridadDesdeCentral(
            Order pedido,
            Instant ahora,
            OperationalSnapshot snapshot
    ) {

        return prioridad(
                pedido,
                ahora,
                estimarViajeDesdeCentral(
                        pedido,
                        snapshot
                )
        );
    }

    /**
     * Evalúa prioridad a partir de una estimación
     * externa del tiempo de viaje.
     */
    public PrioridadPedido prioridad(
            Order pedido,
            Instant ahora,
            Duration viajeEstimado
    ) {

        Objects.requireNonNull(
                pedido,
                "pedido es requerido"
        );

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        Objects.requireNonNull(
                viajeEstimado,
                "viajeEstimado es requerido"
        );

        /*
         * Regla causal fundamental:
         *
         * el optimizador jamás debe conocer
         * un pedido antes de su registeredAt.
         */
        if (
                pedido
                        .registeredAt()
                        .isAfter(
                                ahora
                        )
        ) {

            throw new IllegalArgumentException(
                    "El pedido "
                            +
                            pedido.id()
                            +
                            " todavía no ha sido registrado en "
                            +
                            ahora
            );
        }

        if (
                viajeEstimado.isNegative()
        ) {

            throw new IllegalArgumentException(
                    "viajeEstimado no puede ser negativo"
            );
        }

        Duration espera =
                Duration.between(
                        pedido.registeredAt(),
                        ahora
                );

        /*
         * Todo pedido necesita al menos:
         *
         * viaje
         * +
         * una hora de servicio.
         */
        Duration minimo =
                viajeEstimado.plus(
                        DeliveryStop.SERVICE_TIME
                );

        Duration hastaDeadline =
                Duration.between(
                        ahora,
                        pedido.deadline()
                );

        Duration slack =
                hastaDeadline.minus(
                        minimo
                );

        PrioridadPedido.Nivel nivel;

        if (
                !slack.isPositive()
        ) {

            nivel =
                    PrioridadPedido.Nivel.CRITICA;

        } else if (
                slack.compareTo(
                        UMBRAL_URGENTE
                )
                        <=
                        0
        ) {

            nivel =
                    PrioridadPedido.Nivel.URGENTE;

        } else if (
                slack.compareTo(
                        UMBRAL_ALTA
                )
                        <=
                        0
        ) {

            nivel =
                    PrioridadPedido.Nivel.ALTA;

        } else {

            nivel =
                    PrioridadPedido.Nivel.NORMAL;
        }

        return new PrioridadPedido(
                nivel,
                slack,
                espera,
                minimo
        );
    }

    /**
     * Orden operacional:
     *
     * 1. nivel de urgencia;
     * 2. menor slack;
     * 3. mayor tiempo esperando;
     * 4. registro;
     * 5. id determinista.
     */
    public List<Order> ordenarDesdeCentral(
            Collection<Order> pedidos,
            Instant ahora,
            OperationalSnapshot snapshot
    ) {

        Objects.requireNonNull(
                pedidos,
                "pedidos son requeridos"
        );

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        Objects.requireNonNull(
                snapshot,
                "snapshot es requerido"
        );

        List<Clasificado> clasificados =
                new ArrayList<>(
                        pedidos.size()
                );

        for (
                Order pedido :
                pedidos
        ) {

            clasificados.add(
                    new Clasificado(
                            pedido,

                            prioridadDesdeCentral(
                                    pedido,
                                    ahora,
                                    snapshot
                            )
                    )
            );
        }

        clasificados.sort(
                Comparator
                        .comparingInt(
                                (
                                        Clasificado clasificado
                                ) ->
                                        clasificado
                                                .prioridad()
                                                .nivel()
                                                .ordinal()
                        )

                        .thenComparing(
                                clasificado ->
                                        clasificado
                                                .prioridad()
                                                .slack()
                        )

                        .thenComparing(
                                (
                                        Clasificado clasificado
                                ) ->
                                        clasificado
                                                .prioridad()
                                                .espera(),

                                Comparator.reverseOrder()
                        )

                        .thenComparing(
                                clasificado ->
                                        clasificado
                                                .pedido()
                                                .registeredAt()
                        )

                        .thenComparing(
                                clasificado ->
                                        clasificado
                                                .pedido()
                                                .id()
                        )
        );

        return clasificados
                .stream()
                .map(
                        Clasificado::pedido
                )
                .toList();
    }

    /**
     * Estimación optimista desde el almacén central.
     *
     * No ejecuta shortestPath.
     *
     * Se utiliza solo para prioridad, no para
     * decidir factibilidad.
     */
    public Duration estimarViajeDesdeCentral(
            Order pedido,
            OperationalSnapshot snapshot
    ) {

        Objects.requireNonNull(
                pedido,
                "pedido es requerido"
        );

        Objects.requireNonNull(
                snapshot,
                "snapshot es requerido"
        );

        Warehouse central =
                snapshot
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

        /*
         * Utilizamos la velocidad máxima de un
         * vehículo realmente presente en el snapshot.
         *
         * Es una estimación optimista del mínimo
         * tiempo posible.
         */
        double velocidadMaxima =
                snapshot
                        .vehiclesById()
                        .values()
                        .stream()

                        .map(
                                VehicleOperationalState::vehicle
                        )

                        .mapToDouble(
                                vehiculo ->
                                        snapshot
                                                .fleetProfile()
                                                .parametersFor(
                                                        vehiculo.type()
                                                )
                                                .speedKmPerHour()
                        )

                        .max()

                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "El snapshot requiere al menos un vehículo"
                                        )
                        );

        return estimarViajeManhattan(
                central.location(),
                pedido.destination(),
                velocidadMaxima
        );
    }

    /**
     * Aproximación O(1).
     *
     * Solo sirve para ranking/prioridad.
     *
     * La ruta real continúa siendo calculada
     * posteriormente por RoadNetwork/RouteScheduler.
     */
    public Duration estimarViajeManhattan(
            Location origen,
            Location destino,
            double velocidadKmH
    ) {

        Objects.requireNonNull(
                origen,
                "origen es requerido"
        );

        Objects.requireNonNull(
                destino,
                "destino es requerido"
        );

        if (
                !Double.isFinite(
                        velocidadKmH
                )
                ||
                velocidadKmH <=
                        0
        ) {

            throw new IllegalArgumentException(
                    "velocidadKmH debe ser positiva"
            );
        }

        double horas =
                origen
                        .manhattanDistanceTo(
                                destino
                        )
                        /
                        velocidadKmH;

        long millis =
                (
                        long
                ) Math.ceil(
                        horas
                                *
                                3_600_000.0
                );

        return Duration.ofMillis(
                Math.max(
                        0L,
                        millis
                )
        );
    }

    /**
     * Política para la siguiente fase:
     *
     * un desvío del tramo actualmente recorrido
     * solo se justificará inicialmente para un
     * pedido crítico.
     */
    public boolean permiteDesvioEnTransito(
            PrioridadPedido prioridad
    ) {

        return Objects
                .requireNonNull(
                        prioridad,
                        "prioridad es requerida"
                )
                .critica();
    }

    /**
     * Para crítico/urgente preferimos llegar antes.
     *
     * Para alta/normal preferimos menor costo.
     */
    public boolean priorizaLlegadaSobreCosto(
            PrioridadPedido prioridad
    ) {

        return Objects
                .requireNonNull(
                        prioridad,
                        "prioridad es requerida"
                )
                .urgenteOCritica();
    }

    private record Clasificado(
            Order pedido,
            PrioridadPedido prioridad
    ) {
    }
}