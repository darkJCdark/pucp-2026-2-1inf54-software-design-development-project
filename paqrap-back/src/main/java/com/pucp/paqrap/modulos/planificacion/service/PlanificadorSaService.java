package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.alns.AlnsConfig;
import com.pucp.paqrap.modulos.planificacion.algoritmo.alns.OperationalAlnsPlanner;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.OperationalSimulatedAnnealingPlanner;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;

/**
 * Servicio de planificación operacional.
 *
 * Se conserva el nombre histórico PlanificadorSaService
 * para no romper el resto del backend.
 *
 * Dependiendo de la configuración puede ejecutar:
 *
 * - Simulated Annealing (SA)
 * - Adaptive Large Neighborhood Search (ALNS)
 */
@Service
public class PlanificadorSaService
        implements PlanificadorService {

    /*
     * =====================================================
     * CONFIGURACIÓN SA
     * =====================================================
     */

    static final AnnealingConfig CONFIG_POR_DEFECTO =
            new AnnealingConfig(
                    1000.0,
                    1.0,
                    0.95,
                    50,
                    1_000_000,
                    1_000_000
            );

    /*
     * =====================================================
     * CONFIGURACIÓN ALNS
     * =====================================================
     */

    static final AlnsConfig CONFIG_ALNS_POR_DEFECTO =
            new AlnsConfig(
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

    static final long PRESUPUESTO_POR_DEFECTO_MS =
            1_500L;

    private final FuenteDatosOperativos fuente;

    private final AnnealingConfig configSa;

    private final AlnsConfig configAlns;

    private final AlgoritmoPlanificacion algoritmo;

    /*
     * SA y ALNS comparten exactamente el mismo evaluador.
     */
    private final OperationalPlanEvaluator evaluador;

    private final OperationalSimulatedAnnealingPlanner planificadorSa;

    private final OperationalAlnsPlanner planificadorAlns;

    private final PlanResponseMapper mapper =
            new PlanResponseMapper();

    /*
     * =====================================================
     * CONFIGURACIÓN PÚBLICA
     * =====================================================
     */

    public static AnnealingConfig configuracionPorDefecto() {

        return CONFIG_POR_DEFECTO;
    }

    public static AnnealingConfig configuracionConParametros(
            double temperaturaInicial,
            double temperaturaMinima,
            double factorEnfriamiento,
            int iteracionesPorTemperatura
    ) {

        return new AnnealingConfig(
                temperaturaInicial,
                temperaturaMinima,
                factorEnfriamiento,
                iteracionesPorTemperatura,
                CONFIG_POR_DEFECTO.maximumIterations(),
                CONFIG_POR_DEFECTO.maximumIterationsWithoutImprovement()
        );
    }

    /*
     * =====================================================
     * CONSTRUCTOR SPRING
     * =====================================================
     */

    @Autowired
    public PlanificadorSaService(
            FuenteDatosOperativos fuente,

            @Value(
                    "${paqrap.planificacion.max-tramo-km:0}"
            )
            double maxTramoKm,

            @Value(
                    "${paqrap.planificacion.algoritmo:SA}"
            )
            String algoritmo,

            @Value(
                    "${paqrap.planificacion.alns.initial-batch-size:32}"
            )
            int initialBatchSize,

            @Value(
                    "${paqrap.planificacion.alns.candidate-vehicles:6}"
            )
            int candidateVehicles,

            @Value(
                    "${paqrap.planificacion.alns.min-routes-to-remove:1}"
            )
            int minimumRoutesToRemove,

            @Value(
                    "${paqrap.planificacion.alns.max-routes-to-remove:6}"
            )
            int maximumRoutesToRemove,

            @Value(
                    "${paqrap.planificacion.alns.max-iterations:800}"
            )
            int maximumIterations,

            @Value(
                    "${paqrap.planificacion.alns.max-iterations-without-improvement:200}"
            )
            int maximumIterationsWithoutImprovement,

            @Value(
                    "${paqrap.planificacion.alns.initial-temperature:50.0}"
            )
            double initialTemperature,

            @Value(
                    "${paqrap.planificacion.alns.minimum-temperature:0.5}"
            )
            double minimumTemperature,

            @Value(
                    "${paqrap.planificacion.alns.cooling-factor:0.995}"
            )
            double coolingFactor,

            @Value(
                    "${paqrap.planificacion.alns.reaction-factor:0.20}"
            )
            double reactionFactor
    ) {

        this(
                fuente,

                CONFIG_POR_DEFECTO,

                new AlnsConfig(
                        initialBatchSize,
                        candidateVehicles,
                        minimumRoutesToRemove,
                        maximumRoutesToRemove,
                        maximumIterations,
                        maximumIterationsWithoutImprovement,
                        initialTemperature,
                        minimumTemperature,
                        coolingFactor,
                        reactionFactor
                ),

                AlgoritmoPlanificacion.desde(
                        algoritmo
                ),

                maxTramoKm
        );
    }

    /*
     * =====================================================
     * CONSTRUCTORES HISTÓRICOS
     * =====================================================
     *
     * Se conservan porque varios tests antiguos crean
     * directamente este servicio.
     */

    PlanificadorSaService(
            FuenteDatosOperativos fuente
    ) {

        this(
                fuente,
                CONFIG_POR_DEFECTO,
                CONFIG_ALNS_POR_DEFECTO,
                AlgoritmoPlanificacion.SA,
                0
        );
    }

    /**
     * Constructor necesario para:
     *
     * ConfiguracionSaEscenarioIntegrationTest
     *
     * que utiliza:
     *
     * super(fuente, maxTramoKm)
     */
    PlanificadorSaService(
            FuenteDatosOperativos fuente,
            double maxTramoKm
    ) {

        this(
                fuente,
                CONFIG_POR_DEFECTO,
                CONFIG_ALNS_POR_DEFECTO,
                AlgoritmoPlanificacion.SA,
                maxTramoKm
        );
    }

    PlanificadorSaService(
            FuenteDatosOperativos fuente,
            AnnealingConfig config
    ) {

        this(
                fuente,
                config,
                CONFIG_ALNS_POR_DEFECTO,
                AlgoritmoPlanificacion.SA,
                0
        );
    }

    PlanificadorSaService(
            FuenteDatosOperativos fuente,
            AnnealingConfig config,
            double maxTramoKm
    ) {

        this(
                fuente,
                config,
                CONFIG_ALNS_POR_DEFECTO,
                AlgoritmoPlanificacion.SA,
                maxTramoKm
        );
    }

    /**
     * Constructor general usado internamente.
     */
    PlanificadorSaService(
            FuenteDatosOperativos fuente,
            AnnealingConfig configSa,
            AlnsConfig configAlns,
            AlgoritmoPlanificacion algoritmo,
            double maxTramoKm
    ) {

        this.fuente =
                Objects.requireNonNull(
                        fuente,
                        "fuente es requerida"
                );

        this.configSa =
                Objects.requireNonNull(
                        configSa,
                        "configSa es requerida"
                );

        this.configAlns =
                Objects.requireNonNull(
                        configAlns,
                        "configAlns es requerida"
                );

        this.algoritmo =
                Objects.requireNonNull(
                        algoritmo,
                        "algoritmo es requerido"
                );

        /*
         * Evaluador único.
         *
         * Esto garantiza que SA y ALNS utilicen las mismas
         * reglas de factibilidad.
         */
        this.evaluador =
                new OperationalPlanEvaluator(
                        new RouteScheduler(
                                new RoadNetwork()
                        ),
                        maxTramoKm
                );

        this.planificadorSa =
                new OperationalSimulatedAnnealingPlanner(
                        evaluador
                );

        this.planificadorAlns =
                new OperationalAlnsPlanner(
                        evaluador
                );
    }

    /*
     * =====================================================
     * API INTERNA
     * =====================================================
     */

    /**
     * ReplanificacionSaService utiliza este mismo evaluador
     * para comprobar rutas conservadas y rutas activas
     * modificadas.
     */
    OperationalPlanEvaluator evaluador() {

        return evaluador;
    }

    /**
     * Permite a la replanificación saber si debe aplicar
     * comportamiento específico de ALNS.
     */
    AlgoritmoPlanificacion algoritmo() {

        return algoritmo;
    }

    /*
     * =====================================================
     * PLANIFICACIÓN NORMAL
     * =====================================================
     */

    @Override
    public PlanResponse planificar(
            SolicitudPlanificacion solicitud
    ) {

        Objects.requireNonNull(
                solicitud,
                "solicitud es requerida"
        );

        OperationalSnapshot snapshot =
                construirSnapshot(
                        solicitud.horaPlanificacion()
                );

        long semilla =
                semillaDe(
                        solicitud
                );

        long presupuestoMs =
                presupuestoDe(
                        solicitud
                );

        AnnealingConfig configuracionSa =
                solicitud.configuracionSa() != null
                        ? solicitud.configuracionSa()
                        : configSa;

        EjecucionSa ejecucion =
                ejecutar(
                        snapshot,
                        solicitud.pedidosPendientes(),
                        solicitud.bloqueosActivos(),
                        configuracionSa,
                        semilla,
                        presupuestoMs
                );

        return mapper.aResponse(
                solicitud.modo(),
                solicitud.horaPlanificacion(),
                ejecucion.resultado(),
                semilla,
                ejecucion.duracionMs(),
                presupuestoMs
        );
    }

    /*
     * =====================================================
     * RESULTADO INTERNO
     * =====================================================
     *
     * Se conserva el nombre histórico EjecucionSa para
     * mantener compatibilidad.
     *
     * Puede contener una ejecución SA o ALNS.
     */

    record EjecucionSa(
            ResultadoPlanificacion resultado,
            long duracionMs
    ) {
    }

    /*
     * =====================================================
     * MÉTODOS HISTÓRICOS
     * =====================================================
     *
     * ReplanificacionSaService continúa llamando ejecutarSa().
     *
     * Internamente este método selecciona SA o ALNS.
     */

    EjecucionSa ejecutarSa(
            OperationalSnapshot snapshot,
            List<Order> pedidos,
            List<RoadBlock> bloqueos,
            long semilla,
            long presupuestoMs
    ) {

        return ejecutar(
                snapshot,
                pedidos,
                bloqueos,
                configSa,
                semilla,
                presupuestoMs
        );
    }

    EjecucionSa ejecutarSa(
            OperationalSnapshot snapshot,
            List<Order> pedidos,
            List<RoadBlock> bloqueos,
            AnnealingConfig configuracionSa,
            long semilla,
            long presupuestoMs
    ) {

        return ejecutar(
                snapshot,
                pedidos,
                bloqueos,
                configuracionSa,
                semilla,
                presupuestoMs
        );
    }

    /*
     * =====================================================
     * SELECTOR SA / ALNS
     * =====================================================
     */

    private EjecucionSa ejecutar(
            OperationalSnapshot snapshot,
            List<Order> pedidos,
            List<RoadBlock> bloqueos,
            AnnealingConfig configuracionSa,
            long semilla,
            long presupuestoMs
    ) {

        long inicio =
                System.nanoTime();

        ResultadoPlanificacion resultado;

        if (
                algoritmo ==
                        AlgoritmoPlanificacion.ALNS
        ) {

            /*
             * =============================================
             * ALNS
             * =============================================
             */

            resultado =
                    planificadorAlns.planificar(
                            snapshot,
                            pedidos,
                            bloqueos,
                            configAlns,
                            new Random(
                                    semilla
                            ),
                            presupuestoMs
                    );

        } else {

            /*
             * =============================================
             * SIMULATED ANNEALING
             * =============================================
             */

            resultado =
                    planificadorSa.planificar(
                            snapshot,
                            pedidos,
                            bloqueos,
                            configuracionSa,
                            new Random(
                                    semilla
                            ),
                            presupuestoMs
                    );
        }

        long duracionMs =
                (
                        System.nanoTime()
                                -
                                inicio
                )
                        /
                        1_000_000L;

        return new EjecucionSa(
                resultado,
                duracionMs
        );
    }

    /*
     * =====================================================
     * SEMILLA / PRESUPUESTO
     * =====================================================
     */

    static long semillaDe(
            SolicitudPlanificacion solicitud
    ) {

        return solicitud.semilla() != null
                ? solicitud.semilla()
                : ThreadLocalRandom
                        .current()
                        .nextLong();
    }

    static long presupuestoDe(
            SolicitudPlanificacion solicitud
    ) {

        return solicitud.presupuestoMs() != null
                ? solicitud.presupuestoMs()
                : PRESUPUESTO_POR_DEFECTO_MS;
    }

    /*
     * =====================================================
     * SNAPSHOT
     * =====================================================
     */

    private OperationalSnapshot construirSnapshot(
            Instant hora
    ) {

        Warehouse central =
                fuente
                        .almacenes()
                        .stream()
                        .filter(
                                Warehouse::isCentral
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "La fuente de datos no define un almacén central"
                                        )
                        );

        Map<
                String,
                VehicleOperationalState
                > estados =
                new LinkedHashMap<>();

        for (
                Vehicle vehiculo :
                fuente.flota()
        ) {

            estados.put(
                    vehiculo.id(),

                    new VehicleOperationalState(
                            vehiculo,
                            VehicleStatus.AVAILABLE,
                            central.location(),
                            hora
                    )
            );
        }

        return new OperationalSnapshot(
                hora,

                fuente.perfilFlota(),

                InventorySnapshot.from(
                        fuente.almacenes()
                ),

                estados,

                fuente.calendarioMantenimiento(),

                new ShiftSchedule(
                        ShiftSchedule.DEFAULT_ZONE
                ),

                fuente.averias(
                        null
                )
        );
    }
}