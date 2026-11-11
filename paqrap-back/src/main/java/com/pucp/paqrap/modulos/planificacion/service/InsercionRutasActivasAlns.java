package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolation;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanViolationType;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledDeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ScheduledRouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryRoute;
import com.pucp.paqrap.modulos.planificacion.entity.DeliveryStop;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.RouteStop;
import com.pucp.paqrap.modulos.planificacion.entity.WarehouseVisit;
import com.pucp.paqrap.modulos.planificacion.policy.PoliticaPlanificacion;
import com.pucp.paqrap.modulos.planificacion.policy.PrioridadPedido;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Inserción dinámica en rutas activas.
 *
 * Estrategias:
 *
 * NORMAL / ALTA:
 * - inserción futura normal.
 *
 * URGENTE:
 * - prioriza tiempo de llegada.
 *
 * CRÍTICA:
 * - inserción normal;
 * - desvío desde checkpoint;
 * - UrgencyRemoval de pedidos futuros todavía no cargados.
 *
 * Redistribución:
 *
 * Si una ruta expulsa un pedido mediante UrgencyRemoval,
 * el pedido vuelve inmediatamente a la cola del mismo ciclo.
 *
 * Además:
 *
 * - no puede volver a la ruta que lo expulsó;
 * - puede probar otras rutas activas;
 * - si ninguna lo absorbe, vuelve a pendientes para ALNS;
 * - existe un límite de redistribuciones para impedir ciclos.
 */
final class InsercionRutasActivasAlns {

    /**
     * Evita cadenas patológicas:
     *
     * TA01 -> TA02 -> TA03 -> TA01 -> ...
     *
     * En esta primera versión, dos desplazamientos son
     * suficientes para permitir redistribución sin abrir
     * una búsqueda combinatoria grande.
     */
    private static final int MAX_REDISTRIBUCIONES_POR_PEDIDO =
            2;

    private final OperationalPlanEvaluator evaluador;

    private final PoliticaPlanificacion politica;

    private final DesvioCriticoAlnsService desviosCriticos;

    private final UrgencyRemovalAlnsService urgencyRemoval;

    InsercionRutasActivasAlns(
            OperationalPlanEvaluator evaluador
    ) {

        this(
                evaluador,
                new PoliticaPlanificacion()
        );
    }

    InsercionRutasActivasAlns(
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

        this.desviosCriticos =
                new DesvioCriticoAlnsService(
                        evaluador,
                        politica
                );

        this.urgencyRemoval =
                new UrgencyRemovalAlnsService(
                        evaluador,
                        politica
                );
    }

    Resultado insertar(
            Instant ahora,
            List<RutaVigente> rutasActivas,
            List<Order> pendientes,
            Warehouse central,
            List<RoadBlock> bloqueos,
            long deadlineNanos
    ) {

        Objects.requireNonNull(
                ahora,
                "ahora es requerido"
        );

        Objects.requireNonNull(
                rutasActivas,
                "rutasActivas son requeridas"
        );

        Objects.requireNonNull(
                pendientes,
                "pendientes son requeridos"
        );

        Objects.requireNonNull(
                central,
                "central es requerido"
        );

        Objects.requireNonNull(
                bloqueos,
                "bloqueos son requeridos"
        );

        if (
                rutasActivas.isEmpty()
        ) {

            return new Resultado(
                    rutasActivas,
                    pendientes,
                    List.of()
            );
        }

        List<RutaVigente> actuales =
                new ArrayList<>(
                        rutasActivas
                );

        /*
         * =================================================
         * RESULTADOS
         * =================================================
         */

        Map<String, Order> restantes =
                new LinkedHashMap<>();

        Set<String> insertados =
                new LinkedHashSet<>();

        /*
         * =================================================
         * COLA DINÁMICA
         * =================================================
         *
         * A diferencia de la versión anterior, un pedido
         * puede:
         *
         * 1. entrar en la cola;
         * 2. ser insertado;
         * 3. posteriormente ser desplazado por un crítico;
         * 4. regresar a la cola;
         * 5. terminar en otro vehículo.
         */

        Deque<Order> cola =
                new ArrayDeque<>(
                        politica
                                .ordenarDesdeCentral(
                                        pendientes,
                                        ahora,

                                        rutasActivas
                                                .getFirst()
                                                .snapshotOrigen()
                                )
                );

        Set<String> actualmenteEnCola =
                new HashSet<>();

        for (
                Order pedido :
                cola
        ) {

            actualmenteEnCola.add(
                    pedido.id()
            );
        }

        /*
         * =================================================
         * HISTORIAL DE REDISTRIBUCIÓN
         * =================================================
         */

        Map<
                String,
                Set<String>
                > rutasVetadasPorPedido =
                new HashMap<>();

        Map<
                String,
                Integer
                > numeroRedistribuciones =
                new HashMap<>();

        while (
                !cola.isEmpty()
        ) {

            /*
             * Si se acabó el presupuesto, todos los
             * pedidos restantes de la cola vuelven al
             * ALNS general.
             */
            if (
                    deadlineAlcanzado(
                            deadlineNanos
                    )
            ) {

                while (
                        !cola.isEmpty()
                ) {

                    Order pendiente =
                            cola.removeFirst();

                    actualmenteEnCola.remove(
                            pendiente.id()
                    );

                    restantes.put(
                            pendiente.id(),
                            pendiente
                    );
                }

                break;
            }

            Order pedido =
                    cola.removeFirst();

            actualmenteEnCola.remove(
                    pedido.id()
            );

            PrioridadPedido prioridad =
                    politica
                            .prioridadDesdeCentral(
                                    pedido,
                                    ahora,

                                    actuales
                                            .getFirst()
                                            .snapshotOrigen()
                            );

            Set<String> rutasVetadas =
                    rutasVetadasPorPedido
                            .getOrDefault(
                                    pedido.id(),
                                    Set.of()
                            );

            Candidato mejor =
                    null;

            for (
                    int indiceRuta = 0;
                    indiceRuta < actuales.size();
                    indiceRuta++
            ) {

                if (
                        deadlineAlcanzado(
                                deadlineNanos
                        )
                ) {
                    break;
                }

                RutaVigente vigente =
                        actuales.get(
                                indiceRuta
                        );

                String rutaId =
                        vigente
                                .ruta()
                                .id();

                /*
                 * Un pedido desplazado no puede regresar
                 * inmediatamente a la ruta que lo expulsó.
                 */
                if (
                        rutasVetadas.contains(
                                rutaId
                        )
                ) {

                    continue;
                }

                if (
                        !esCandidata(
                                vigente,
                                pedido
                        )
                ) {

                    continue;
                }

                /*
                 * =================================================
                 * A. INSERCIÓN NORMAL
                 * =================================================
                 */

                Candidato mejorRuta =
                        mejorInsercionEnRuta(
                                ahora,
                                vigente,
                                pedido,
                                prioridad,
                                central,
                                bloqueos,
                                indiceRuta,
                                deadlineNanos
                        );

                /*
                 * =================================================
                 * B. DESVÍO CRÍTICO
                 * =================================================
                 */

                if (
                        prioridad.critica()
                        &&
                        !deadlineAlcanzado(
                                deadlineNanos
                        )
                ) {

                    var desvio =
                            desviosCriticos.intentar(
                                    ahora,
                                    vigente,
                                    pedido,
                                    central,
                                    bloqueos,
                                    deadlineNanos
                            );

                    if (
                            desvio.isPresent()
                    ) {

                        DesvioCriticoAlnsService.Resultado d =
                                desvio.orElseThrow();

                        mejorRuta =
                                elegirMejor(
                                        mejorRuta,

                                        new Candidato(
                                                indiceRuta,
                                                d.ruta(),
                                                d.deltaCosto(),
                                                d.llegadaPedido(),
                                                true,
                                                List.of()
                                        ),

                                        prioridad
                                );
                    }

                    /*
                     * =================================================
                     * C. URGENCY REMOVAL
                     * =================================================
                     */

                    if (
                            !deadlineAlcanzado(
                                    deadlineNanos
                            )
                    ) {

                        var redistribucion =
                                urgencyRemoval.intentar(
                                        ahora,
                                        actuales,
                                        indiceRuta,
                                        pedido,
                                        central,
                                        bloqueos,
                                        deadlineNanos
                                );

                        if (
                                redistribucion.isPresent()
                        ) {

                            UrgencyRemovalAlnsService.Resultado r =
                                    redistribucion.orElseThrow();

                            mejorRuta =
                                    elegirMejor(
                                            mejorRuta,

                                            new Candidato(
                                                    indiceRuta,
                                                    r.ruta(),
                                                    r.deltaCosto(),
                                                    r.llegadaCritico(),
                                                    true,

                                                    List.of(
                                                            r.desplazado()
                                                    )
                                            ),

                                            prioridad
                                    );
                        }
                    }
                }

                mejor =
                        elegirMejor(
                                mejor,
                                mejorRuta,
                                prioridad
                        );
            }

            /*
             * =================================================
             * NO SE PUDO ASIGNAR
             * =================================================
             */

            if (
                    mejor == null
            ) {

                restantes.put(
                        pedido.id(),
                        pedido
                );

                insertados.remove(
                        pedido.id()
                );

                continue;
            }

            /*
             * =================================================
             * APLICAR CANDIDATO
             * =================================================
             */

            RutaVigente rutaAnterior =
                    actuales.get(
                            mejor.indiceRuta()
                    );

            String rutaOrigenId =
                    rutaAnterior
                            .ruta()
                            .id();

            actuales.set(
                    mejor.indiceRuta(),
                    mejor.ruta()
            );

            /*
             * El pedido actual ya quedó asignado.
             */
            restantes.remove(
                    pedido.id()
            );

            insertados.add(
                    pedido.id()
            );

            /*
             * =================================================
             * REENCOLAR DESPLAZADOS
             * =================================================
             */

            for (
                    Order desplazado :
                    mejor.desplazados()
            ) {

                /*
                 * Si estaba marcado como insertado durante
                 * este mismo ciclo, deja de estarlo hasta
                 * que encuentre una nueva ruta.
                 */
                insertados.remove(
                        desplazado.id()
                );

                restantes.remove(
                        desplazado.id()
                );

                /*
                 * Registrar la ruta que lo expulsó.
                 */
                rutasVetadasPorPedido
                        .computeIfAbsent(
                                desplazado.id(),
                                ignored ->
                                        new LinkedHashSet<>()
                        )
                        .add(
                                rutaOrigenId
                        );

                int redistribuciones =
                        numeroRedistribuciones.merge(
                                desplazado.id(),
                                1,
                                Integer::sum
                        );

                /*
                 * Protección anti-loop.
                 */
                if (
                        redistribuciones
                                >
                                MAX_REDISTRIBUCIONES_POR_PEDIDO
                ) {

                    restantes.put(
                            desplazado.id(),
                            desplazado
                    );

                    continue;
                }

                /*
                 * Puede volver a la cola incluso si ya fue
                 * procesado previamente.
                 *
                 * Lo único que evitamos es tener dos copias
                 * simultáneas dentro de la cola.
                 */
                if (
                        actualmenteEnCola.add(
                                desplazado.id()
                        )
                ) {

                    cola.addLast(
                            desplazado
                    );
                }
            }
        }

        return new Resultado(
                actuales,

                new ArrayList<>(
                        restantes.values()
                ),

                new ArrayList<>(
                        insertados
                )
        );
    }

    /*
     * =====================================================
     * ELEGIBILIDAD
     * =====================================================
     */

    private boolean esCandidata(
            RutaVigente ruta,
            Order pedido
    ) {

        if (
                ruta.programada() == null
        ) {
            return false;
        }

        if (
                ruta
                        .ruta()
                        .id()
                        .startsWith(
                                "REGRESO-"
                        )
        ) {

            return false;
        }

        int capacidad =
                ruta
                        .snapshotOrigen()
                        .fleetProfile()
                        .parametersFor(
                                ruta
                                        .ruta()
                                        .vehicle()
                                        .type()
                        )
                        .capacity();

        /*
         * Esta capa rápida todavía no divide un pedido
         * nuevo entre varios vehículos.
         */
        if (
                pedido.packages()
                        >
                        capacidad
        ) {

            return false;
        }

        /*
         * No duplicar el mismo pedido dentro de una ruta.
         */
        return ruta
                .ruta()
                .stops()
                .stream()

                .filter(
                        DeliveryStop
                                .class::
                                isInstance
                )

                .map(
                        DeliveryStop
                                .class::
                                cast
                )

                .noneMatch(
                        entrega ->
                                entrega
                                        .order()
                                        .id()
                                        .equals(
                                                pedido.id()
                                        )
                );
    }

    /*
     * =====================================================
     * INSERCIÓN NORMAL
     * =====================================================
     */

    private Candidato mejorInsercionEnRuta(
            Instant ahora,
            RutaVigente vigente,
            Order pedido,
            PrioridadPedido prioridad,
            Warehouse central,
            List<RoadBlock> bloqueos,
            int indiceRuta,
            long deadlineNanos
    ) {

        DeliveryRoute original =
                vigente.ruta();

        ScheduledDeliveryRoute programada =
                vigente.programada();

        int prefijoComprometido =
                prefijoComprometido(
                        original,
                        programada,
                        ahora
                );

        List<RouteStop> stops =
                original.stops();

        int limiteInsercion =
                stops.size();

        if (
                original.endsAtWarehouse()
        ) {
            limiteInsercion--;
        }

        if (
                prefijoComprometido
                        >
                        limiteInsercion
        ) {
            return null;
        }

        Candidato mejor =
                null;

        for (
                int posicion =
                        prefijoComprometido;

                posicion <=
                        limiteInsercion;

                posicion++
        ) {

            if (
                    deadlineAlcanzado(
                            deadlineNanos
                    )
            ) {
                break;
            }

            /*
             * =================================================
             * DIRECTA
             * =================================================
             */

            DeliveryRoute directa =
                    original
                            .withInsertedStop(
                                    posicion,

                                    new DeliveryStop(
                                            pedido,
                                            pedido.packages()
                                    )
                            );

            Candidato directo =
                    validar(
                            vigente,
                            directa,
                            pedido,
                            bloqueos,
                            indiceRuta,
                            false
                    );

            mejor =
                    elegirMejor(
                            mejor,
                            directo,
                            prioridad
                    );

            if (
                    deadlineAlcanzado(
                            deadlineNanos
                    )
            ) {
                break;
            }

            /*
             * =================================================
             * RECARGA + ENTREGA
             * =================================================
             */

            int cargaAntes =
                    cargaAntesDePosicion(
                            programada,
                            original,
                            posicion
                    );

            int capacidad =
                    vigente
                            .snapshotOrigen()
                            .fleetProfile()
                            .parametersFor(
                                    original
                                            .vehicle()
                                            .type()
                            )
                            .capacity();

            if (
                    cargaAntes
                            +
                            pedido.packages()
                            <=
                            capacidad
            ) {

                DeliveryRoute conRecarga =
                        original
                                .withInsertedStop(
                                        posicion,

                                        new WarehouseVisit(
                                                central,
                                                pedido.packages()
                                        )
                                )

                                .withInsertedStop(
                                        posicion + 1,

                                        new DeliveryStop(
                                                pedido,
                                                pedido.packages()
                                        )
                                );

                Candidato recarga =
                        validar(
                                vigente,
                                conRecarga,
                                pedido,
                                bloqueos,
                                indiceRuta,
                                false
                        );

                mejor =
                        elegirMejor(
                                mejor,
                                recarga,
                                prioridad
                        );
            }
        }

        return mejor;
    }

    /*
     * =====================================================
     * PARTE COMPROMETIDA
     * =====================================================
     */

    private int prefijoComprometido(
            DeliveryRoute ruta,
            ScheduledDeliveryRoute programada,
            Instant ahora
    ) {

        int comprometidas =
                0;

        Instant partida =
                ruta.departureAt();

        List<ScheduledRouteStop> programadas =
                programada.scheduledStops();

        for (
                int i = 0;
                i < programadas.size();
                i++
        ) {

            if (
                    partida.isAfter(
                            ahora
                    )
            ) {
                break;
            }

            comprometidas =
                    i + 1;

            partida =
                    programadas
                            .get(i)
                            .completedAt();
        }

        return comprometidas;
    }

    private int cargaAntesDePosicion(
            ScheduledDeliveryRoute programada,
            DeliveryRoute ruta,
            int posicion
    ) {

        if (
                posicion <= 0
        ) {
            return ruta.initialLoad();
        }

        return programada
                .scheduledStops()
                .get(
                        posicion - 1
                )
                .loadAfter();
    }

    /*
     * =====================================================
     * VALIDACIÓN
     * =====================================================
     */

    private Candidato validar(
            RutaVigente vigente,
            DeliveryRoute candidata,
            Order pedidoNuevo,
            List<RoadBlock> bloqueos,
            int indiceRuta,
            boolean desvio
    ) {

        Map<String, Order> pedidosRuta =
                new LinkedHashMap<>();

        for (
                RouteStop parada :
                candidata.stops()
        ) {

            if (
                    parada instanceof
                            DeliveryStop entrega
            ) {

                pedidosRuta.putIfAbsent(
                        entrega
                                .order()
                                .id(),

                        entrega.order()
                );
            }
        }

        PlanEvaluation evaluacion =
                evaluador.evaluate(
                        OperationalPlan
                                .empty()
                                .withRoute(
                                        candidata
                                ),

                        vigente.snapshotOrigen(),

                        pedidosRuta.values(),

                        bloqueos
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

        ScheduledDeliveryRoute schedule =
                evaluacion
                        .schedulesByRouteId()
                        .get(
                                candidata.id()
                        );

        if (
                schedule == null
        ) {
            return null;
        }

        Instant llegadaPedido =
                schedule
                        .scheduledStops()
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
                                                        pedidoNuevo.id()
                                                )
                        )

                        .map(
                                ScheduledRouteStop::arrivedAt
                        )

                        .findFirst()

                        .orElse(
                                null
                        );

        if (
                llegadaPedido == null
        ) {
            return null;
        }

        double costoAnterior =
                vigente.programada() == null
                        ? 0.0
                        : vigente
                                .programada()
                                .totalCost();

        double delta =
                schedule.totalCost()
                        -
                        costoAnterior;

        return new Candidato(
                indiceRuta,

                new RutaVigente(
                        candidata,
                        vigente.snapshotOrigen(),
                        schedule,
                        List.of()
                ),

                delta,

                llegadaPedido,

                desvio,

                List.of()
        );
    }

    /*
     * =====================================================
     * SELECCIÓN
     * =====================================================
     */

    private Candidato elegirMejor(
            Candidato actual,
            Candidato candidato,
            PrioridadPedido prioridad
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

        /*
         * =================================================
         * CRÍTICA / URGENTE
         * =================================================
         *
         * Primero llegada.
         */

        if (
                politica
                        .priorizaLlegadaSobreCosto(
                                prioridad
                        )
        ) {

            int llegada =
                    candidato
                            .llegadaPedido()
                            .compareTo(
                                    actual
                                            .llegadaPedido()
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
             * Misma llegada:
             *
             * preferimos modificar menos pedidos
             * existentes.
             */
            int desplazamientos =
                    Integer.compare(
                            candidato
                                    .desplazados()
                                    .size(),

                            actual
                                    .desplazados()
                                    .size()
                    );

            if (
                    desplazamientos < 0
            ) {
                return candidato;
            }

            if (
                    desplazamientos > 0
            ) {
                return actual;
            }

            /*
             * Luego costo.
             */
            int costo =
                    Double.compare(
                            candidato.deltaCosto(),
                            actual.deltaCosto()
                    );

            if (
                    costo < 0
            ) {
                return candidato;
            }

            if (
                    costo > 0
            ) {
                return actual;
            }

            /*
             * Empate total:
             *
             * evitar desvíos innecesarios.
             */
            if (
                    actual.desvio()
                            !=
                            candidato.desvio()
            ) {

                return actual.desvio()
                        ? candidato
                        : actual;
            }

            return actual;
        }

        /*
         * =================================================
         * NORMAL / ALTA
         * =================================================
         *
         * Costo primero.
         */

        int costo =
                Double.compare(
                        candidato.deltaCosto(),
                        actual.deltaCosto()
                );

        if (
                costo < 0
        ) {
            return candidato;
        }

        if (
                costo > 0
        ) {
            return actual;
        }

        int llegada =
                candidato
                        .llegadaPedido()
                        .compareTo(
                                actual
                                        .llegadaPedido()
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

        return actual;
    }

    /*
     * =====================================================
     * TIEMPO
     * =====================================================
     */

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

    /*
     * =====================================================
     * RESULTADOS
     * =====================================================
     */

    record Resultado(
            List<RutaVigente> rutas,
            List<Order> pendientes,
            List<String> pedidosInsertados
    ) {

        Resultado {

            rutas =
                    List.copyOf(
                            rutas
                    );

            pendientes =
                    List.copyOf(
                            pendientes
                    );

            pedidosInsertados =
                    List.copyOf(
                            pedidosInsertados
                    );
        }
    }

    private record Candidato(
            int indiceRuta,
            RutaVigente ruta,
            double deltaCosto,
            Instant llegadaPedido,
            boolean desvio,
            List<Order> desplazados
    ) {

        Candidato {

            desplazados =
                    List.copyOf(
                            desplazados
                    );
        }
    }
}