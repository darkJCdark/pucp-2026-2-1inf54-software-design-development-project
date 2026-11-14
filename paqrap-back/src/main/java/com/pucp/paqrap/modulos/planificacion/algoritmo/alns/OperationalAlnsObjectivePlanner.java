package com.pucp.paqrap.modulos.planificacion.algoritmo.alns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.CriterioObjetivoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ObjetivoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;

/**
 * Fachada de ALNS consciente del objetivo de planificación.
 *
 * No modifica todavía el núcleo estable de OperationalAlnsPlanner.
 *
 * SERVICE_THEN_COST:
 *
 *     conserva exactamente el comportamiento ALNS actual.
 *
 * SERVICE_MAX:
 *
 *     1. usa ALNS para construir una solución base;
 *     2. reserva una parte del presupuesto para intentar rescatar
 *        los pedidos no atendidos;
 *     3. cada pedido rescatado se acepta aunque aumente el costo;
 *     4. nunca se elimina un pedido que ya estaba atendido.
 *
 * Esto permite medir el techo de servicio antes de introducir
 * nuevamente el costo en la función objetivo.
 */
public final class OperationalAlnsObjectivePlanner {

    /*
     * SERVICE_MAX no necesita dedicar todo el presupuesto
     * a optimizar costo.
     *
     * 60 %:
     *     construcción + ALNS actual.
     *
     * 40 %:
     *     rescate de pedidos que quedaron fuera.
     */
    private static final double SERVICE_MAX_BASE_SHARE =
            0.60;

    private final OperationalAlnsPlanner delegate;

    private final ServiceFirstSeedRescue serviceRescue;

    public OperationalAlnsObjectivePlanner(
            OperationalPlanEvaluator evaluator
    ) {

        Objects.requireNonNull(
                evaluator,
                "evaluator is required"
        );

        this.delegate =
                new OperationalAlnsPlanner(
                        evaluator
                );

        this.serviceRescue =
                new ServiceFirstSeedRescue(
                        evaluator
                );
    }

    /*
     * =====================================================
     * SIN PRESUPUESTO TEMPORAL EXPLÍCITO
     * =====================================================
     */

    public ResultadoPlanificacion planificar(
            OperationalSnapshot snapshot,
            Collection<Order> orders,
            List<RoadBlock> blocks,
            AlnsConfig config,
            RandomGenerator random,
            ObjetivoPlanificacion objetivo
    ) {

        Objects.requireNonNull(
                objetivo,
                "objetivo is required"
        );

        ResultadoPlanificacion base =
                delegate.planificar(
                        snapshot,
                        orders,
                        blocks,
                        config,
                        random
                );

        if (
                objetivo
                        ==
                        ObjetivoPlanificacion.SERVICE_THEN_COST
        ) {

            return base;
        }

        return rescatarServicio(
                base,
                orders,
                snapshot,
                blocks,
                Long.MAX_VALUE,
                objetivo
        );
    }

    /*
     * =====================================================
     * CON PRESUPUESTO TEMPORAL
     * =====================================================
     */

    public ResultadoPlanificacion planificar(
            OperationalSnapshot snapshot,
            Collection<Order> orders,
            List<RoadBlock> blocks,
            AlnsConfig config,
            RandomGenerator random,
            long presupuestoMs,
            ObjetivoPlanificacion objetivo
    ) {

        Objects.requireNonNull(
                snapshot,
                "snapshot is required"
        );

        Objects.requireNonNull(
                orders,
                "orders are required"
        );

        Objects.requireNonNull(
                blocks,
                "blocks are required"
        );

        Objects.requireNonNull(
                config,
                "config is required"
        );

        Objects.requireNonNull(
                random,
                "random is required"
        );

        Objects.requireNonNull(
                objetivo,
                "objetivo is required"
        );

        if (
                presupuestoMs <= 0
        ) {

            throw new IllegalArgumentException(
                    "presupuestoMs must be positive"
            );
        }

        /*
         * El comportamiento normal debe seguir siendo
         * exactamente el mismo.
         */
        if (
                objetivo
                        ==
                        ObjetivoPlanificacion.SERVICE_THEN_COST
        ) {

            return delegate.planificar(
                    snapshot,
                    orders,
                    blocks,
                    config,
                    random,
                    presupuestoMs
            );
        }

        /*
         * =================================================
         * SERVICE_MAX
         * =================================================
         */

        long startedAt =
                System.nanoTime();

        long deadlineNanos =
                deadlineNanos(
                        startedAt,
                        presupuestoMs
                );

        long presupuestoBaseMs =
                presupuestoBase(
                        presupuestoMs
                );

        ResultadoPlanificacion base =
                delegate.planificar(
                        snapshot,
                        orders,
                        blocks,
                        config,
                        random,
                        presupuestoBaseMs
                );

        /*
         * Si ALNS ya atendió todo, no hay nada que rescatar.
         */
        if (
                base
                        .noAtendidos()
                        .isEmpty()
        ) {

            return base;
        }

        /*
         * Si ALNS consumió por algún motivo todo el tiempo,
         * devolvemos la mejor solución conocida.
         */
        if (
                deadlineReached(
                        deadlineNanos
                )
        ) {

            return base;
        }

        return rescatarServicio(
                base,
                orders,
                snapshot,
                blocks,
                deadlineNanos,
                objetivo
        );
    }

    /*
     * =====================================================
     * RESCATE
     * =====================================================
     */

    private ResultadoPlanificacion rescatarServicio(
            ResultadoPlanificacion base,
            Collection<Order> todosLosPedidos,
            OperationalSnapshot snapshot,
            List<RoadBlock> blocks,
            long deadlineNanos,
            ObjetivoPlanificacion objetivo
    ) {

        if (
                base
                        .noAtendidos()
                        .isEmpty()
        ) {

            return base;
        }

        List<Order> atendidos =
                pedidosAtendidos(
                        todosLosPedidos,
                        base.noAtendidos()
                );

        ServiceFirstSeedRescue.Resultado rescate =
                serviceRescue.rescatar(
                        base.plan(),
                        atendidos,
                        base.noAtendidos(),
                        snapshot,
                        blocks,
                        deadlineNanos
                );

        ResultadoPlanificacion candidato =
                new ResultadoPlanificacion(
                        rescate.plan(),
                        rescate.evaluacion(),
                        rescate.noAtendidos()
                );

        CriterioObjetivoPlanificacion criterio =
                new CriterioObjetivoPlanificacion(
                        objetivo
                );

        /*
         * El rescate está construido para no perder pedidos
         * ya atendidos.
         *
         * Aun así verificamos formalmente que nunca estamos
         * devolviendo una solución peor según el objetivo.
         */
        if (
                criterio.esMejor(
                        base,
                        candidato
                )
        ) {

            throw new IllegalStateException(
                    "Service rescue degraded the planning objective"
            );
        }

        /*
         * Si no consiguió rescatar nada, preferimos conservar
         * literalmente el plan base para no introducir cambios
         * de ruta innecesarios.
         */
        if (
                rescate
                        .cantidadRescatados()
                        ==
                        0
        ) {

            return base;
        }

        return candidato;
    }

    /*
     * =====================================================
     * PEDIDOS ATENDIDOS
     * =====================================================
     */

    private List<Order> pedidosAtendidos(
            Collection<Order> todos,
            Collection<Order> noAtendidos
    ) {

        Set<String> idsNoAtendidos =
                new HashSet<>();

        for (
                Order order :
                noAtendidos
        ) {

            idsNoAtendidos.add(
                    order.id()
            );
        }

        List<Order> atendidos =
                new ArrayList<>();

        for (
                Order order :
                todos
        ) {

            if (
                    !idsNoAtendidos.contains(
                            order.id()
                    )
            ) {

                atendidos.add(
                        order
                );
            }
        }

        return atendidos;
    }

    /*
     * =====================================================
     * PRESUPUESTO
     * =====================================================
     */

    private long presupuestoBase(
            long presupuestoMs
    ) {

        if (
                presupuestoMs == 1
        ) {

            return 1;
        }

        long base =
                Math.round(
                        presupuestoMs
                                *
                                SERVICE_MAX_BASE_SHARE
                );

        /*
         * Reservar siempre al menos 1 ms para rescate.
         */
        return Math.max(
                1,
                Math.min(
                        presupuestoMs - 1,
                        base
                )
        );
    }

    private long deadlineNanos(
            long startedAt,
            long presupuestoMs
    ) {

        long budgetNanos =
                TimeUnit.MILLISECONDS.toNanos(
                        presupuestoMs
                );

        if (
                Long.MAX_VALUE
                        -
                        startedAt
                        <
                        budgetNanos
        ) {

            return Long.MAX_VALUE;
        }

        return startedAt
                +
                budgetNanos;
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
}