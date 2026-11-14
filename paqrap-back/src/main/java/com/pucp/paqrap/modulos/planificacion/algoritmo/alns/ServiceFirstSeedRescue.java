package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.PlanEvaluation;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.policy.PoliticaPlanificacion;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Fase de rescate orientada exclusivamente al nivel de servicio.
 *
 * Problema que resuelve:
 *
 * El seed escalable puede dejar pedidos en "unattended".
 * Después, el ALNS tradicional trabaja principalmente sobre
 * los pedidos que ya consiguió insertar.
 *
 * Por ello, SERVICE_MAX necesita una segunda oportunidad
 * explícita para los pedidos que quedaron fuera.
 *
 * Esta fase:
 *
 * 1. toma el plan seed;
 * 2. ordena los no atendidos por prioridad/slack;
 * 3. para cada pedido prueba la flota disponible completa;
 * 4. si existe una inserción factible, la acepta aunque
 *    aumente mucho el costo;
 * 5. nunca elimina un pedido ya atendido;
 * 6. deja como no atendido únicamente lo que no pudo
 *    insertar dentro del presupuesto disponible.
 *
 * No relaja ninguna restricción operacional:
 *
 * - capacidad;
 * - deadline;
 * - inventario;
 * - bloqueos;
 * - mantenimiento/averías;
 * - refrigerio;
 * - disponibilidad del vehículo.
 */
final class ServiceFirstSeedRescue {

    /**
     * Integer.MAX_VALUE hace que InsertionSupport no limite
     * artificialmente el rescate a candidateVehicles.
     *
     * En PaqRap la flota real es de 37 unidades, por lo que
     * revisar toda la flota para pedidos no atendidos sigue
     * siendo acotado.
     */
    private final InsertionSupport insertionSupport;

    private final OperationalPlanEvaluator evaluator;

    private final PoliticaPlanificacion politica;

    ServiceFirstSeedRescue(
            OperationalPlanEvaluator evaluator
    ) {

        this.evaluator =
                Objects.requireNonNull(
                        evaluator,
                        "evaluator is required"
                );

        this.insertionSupport =
                new InsertionSupport(
                        evaluator,
                        Integer.MAX_VALUE
                );

        this.politica =
                new PoliticaPlanificacion();
    }

    Resultado rescatar(
            OperationalPlan seedPlan,
            Collection<Order> atendidosIniciales,
            Collection<Order> noAtendidosIniciales,
            OperationalSnapshot snapshot,
            List<RoadBlock> blocks,
            long deadlineNanos
    ) {

        Objects.requireNonNull(
                seedPlan,
                "seedPlan is required"
        );

        Objects.requireNonNull(
                atendidosIniciales,
                "atendidosIniciales are required"
        );

        Objects.requireNonNull(
                noAtendidosIniciales,
                "noAtendidosIniciales are required"
        );

        Objects.requireNonNull(
                snapshot,
                "snapshot is required"
        );

        Objects.requireNonNull(
                blocks,
                "blocks are required"
        );

        OperationalPlan actual =
                seedPlan;

        /*
         * LinkedHashMap:
         *
         * - evita duplicados por id;
         * - conserva orden determinista.
         */
        Map<String, Order> atendidos =
                indexar(
                        atendidosIniciales
                );

        List<Order> pendientesOrdenados =
                politica.ordenarDesdeCentral(
                        new ArrayList<>(
                                noAtendidosIniciales
                        ),
                        snapshot.planningTime(),
                        snapshot
                );

        List<Order> noAtendidos =
                new ArrayList<>();

        List<String> rescatados =
                new ArrayList<>();

        for (
                int indice = 0;
                indice < pendientesOrdenados.size();
                indice++
        ) {

            Order pedido =
                    pendientesOrdenados.get(
                            indice
                    );

            /*
             * Si se terminó el presupuesto, no seguimos
             * evaluando. Todos los restantes permanecen
             * explícitamente como no atendidos.
             */
            if (
                    deadlineReached(
                            deadlineNanos
                    )
            ) {

                for (
                        int restante = indice;
                        restante < pendientesOrdenados.size();
                        restante++
                ) {

                    noAtendidos.add(
                            pendientesOrdenados.get(
                                    restante
                            )
                    );
                }

                break;
            }

            /*
             * Protección frente a una entrada inconsistente.
             */
            if (
                    atendidos.containsKey(
                            pedido.id()
                    )
            ) {
                continue;
            }

            DeliveryDemand demanda =
                    new DeliveryDemand(
                            pedido,
                            pedido.packages()
                    );

            /*
             * Para evaluar la inserción solo exigimos:
             *
             * - todos los pedidos ya cubiertos;
             * - el pedido que intentamos rescatar.
             *
             * No incluimos todavía los demás no atendidos,
             * porque OperationalPlanEvaluator correctamente
             * los consideraría faltantes.
             */
            List<Order> requeridos =
                    new ArrayList<>(
                            atendidos.values()
                    );

            requeridos.add(
                    pedido
            );

            var insercion =
                    insertionSupport.bestValidatedInsertion(
                            actual,
                            demanda,
                            snapshot,
                            requeridos,
                            blocks,
                            deadlineNanos
                    );

            if (
                    insercion.isEmpty()
            ) {

                noAtendidos.add(
                        pedido
                );

                continue;
            }

            /*
             * =================================================
             * SERVICE FIRST
             * =================================================
             *
             * No comparamos costo.
             *
             * Si la inserción es factible y rescata un pedido,
             * se acepta.
             */

            InsertionSupport.InsertionCandidate candidato =
                    insercion.orElseThrow();

            actual =
                    candidato.plan();

            atendidos.put(
                    pedido.id(),
                    pedido
            );

            rescatados.add(
                    pedido.id()
            );
        }

        /*
         * Evaluación final consistente con el conjunto
         * efectivamente atendido.
         */
        PlanEvaluation evaluacionFinal =
                evaluator.evaluate(
                        actual,
                        snapshot,
                        atendidos.values(),
                        blocks
                );

        if (
                !evaluacionFinal.isFeasible()
        ) {

            throw new IllegalStateException(
                    "Service-first rescue produced an infeasible plan: "
                            +
                            evaluacionFinal.violations()
            );
        }

        return new Resultado(
                actual,
                evaluacionFinal,
                new ArrayList<>(
                        atendidos.values()
                ),
                noAtendidos,
                rescatados
        );
    }

    private Map<String, Order> indexar(
            Collection<Order> orders
    ) {

        Map<String, Order> indexed =
                new LinkedHashMap<>();

        for (
                Order order :
                orders
        ) {

            Order anterior =
                    indexed.putIfAbsent(
                            order.id(),
                            order
                    );

            if (
                    anterior != null
            ) {

                throw new IllegalArgumentException(
                        "Order id duplicated in rescue input: "
                                +
                                order.id()
                );
            }
        }

        return indexed;
    }

    private boolean deadlineReached(
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
            OperationalPlan plan,
            PlanEvaluation evaluacion,
            List<Order> atendidos,
            List<Order> noAtendidos,
            List<String> pedidosRescatados
    ) {

        Resultado {

            Objects.requireNonNull(
                    plan,
                    "plan is required"
            );

            Objects.requireNonNull(
                    evaluacion,
                    "evaluacion is required"
            );

            atendidos =
                    List.copyOf(
                            atendidos
                    );

            noAtendidos =
                    List.copyOf(
                            noAtendidos
                    );

            pedidosRescatados =
                    List.copyOf(
                            pedidosRescatados
                    );
        }

        int cantidadRescatados() {

            return pedidosRescatados.size();
        }
    }
}