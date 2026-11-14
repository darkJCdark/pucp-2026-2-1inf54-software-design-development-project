package com.pucp.paqrap.modulos.planificacion.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import com.pucp.paqrap.modulos.planificacion.algoritmo.alns.OperationalAlnsObjectivePlanner;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ObjetivoPlanificacion;
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
 * Servicio principal de planificación.
 *
 * El nombre histórico se conserva como PlanificadorSaService
 * para no romper la integración existente con:
 *
 * - ReplanificacionService
 * - PlanificadorSaAdapter
 * - pruebas existentes
 *
 * Sin embargo, el algoritmo utilizado es configurable:
 *
 *     paqrap.planificacion.algoritmo=SA
 *
 * o:
 *
 *     paqrap.planificacion.algoritmo=ALNS
 *
 * Para ALNS también puede configurarse el objetivo:
 *
 *     SERVICE_THEN_COST
 *     SERVICE_MAX
 *
 * SERVICE_MAX:
 *
 *     intenta maximizar el número de pedidos atendidos
 *     dentro del SLA antes de considerar costo.
 *
 * SERVICE_THEN_COST:
 *
 *     mantiene el comportamiento operacional normal:
 *     preservar servicio y optimizar costo.
 */
@Service
public class PlanificadorSaService
        implements PlanificadorService {

    /*
     * =====================================================
     * CONFIGURACIÓN SA
     * =====================================================
     */

    /**
     * Parámetros históricos/formales de Simulated Annealing.
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

    /**
     * Presupuesto por defecto cuando la solicitud no trae uno.
     */
    static final long PRESUPUESTO_POR_DEFECTO_MS =
            1_500L;

    /*
     * =====================================================
     * CONFIGURACIÓN ALNS DEFAULT
     * =====================================================
     *
     * Los valores reales de producción llegan normalmente
     * desde application.yml.
     *
     * Este objeto existe principalmente para mantener
     * compatibles los constructores utilizados directamente
     * por tests que no arrancan Spring.
     */

    static final AlnsConfig ALNS_CONFIG_POR_DEFECTO =
            new AlnsConfig(
                    32,
                    6,
                    1,
                    8,
                    800,
                    800,
                    1000.0,
                    1.0,
                    0.95,
                    0.20
            );

    /*
     * =====================================================
     * DEPENDENCIAS
     * =====================================================
     */

    private final FuenteDatosOperativos fuente;

    private final AnnealingConfig configSa;

    private final AlnsConfig configAlns;

    private final OperationalPlanEvaluator evaluador;

    private final OperationalSimulatedAnnealingPlanner planificadorSa;

    private final OperationalAlnsObjectivePlanner planificadorAlns;

    private final PlanResponseMapper mapper =
            new PlanResponseMapper();

    /*
     * =====================================================
     * CONFIGURACIÓN ACTIVA
     * =====================================================
     */

    private final String algoritmo;

    private final ObjetivoPlanificacion objetivo;

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
                    "${paqrap.planificacion.objetivo:SERVICE_THEN_COST}"
            )
            String objetivo,

            @Value(
                    "${paqrap.planificacion.alns.initial-batch-size:32}"
            )
            int initialBatchSize,

            @Value(
                    "${paqrap.planificacion.alns.candidate-vehicles:6}"
            )
            int candidateVehicles,

            @Value(
                    "${paqrap.planificacion.alns.minimum-removal:1}"
            )
            int minimumRemoval,

            @Value(
                    "${paqrap.planificacion.alns.maximum-removal:8}"
            )
            int maximumRemoval,

            @Value(
                    "${paqrap.planificacion.alns.maximum-iterations:800}"
            )
            int maximumIterations,

            @Value(
                    "${paqrap.planificacion.alns.maximum-iterations-without-improvement:800}"
            )
            int maximumIterationsWithoutImprovement,

            @Value(
                    "${paqrap.planificacion.alns.initial-temperature:1000}"
            )
            double initialTemperature,

            @Value(
                    "${paqrap.planificacion.alns.minimum-temperature:1}"
            )
            double minimumTemperature,

            @Value(
                    "${paqrap.planificacion.alns.cooling-factor:0.95}"
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
                        minimumRemoval,
                        maximumRemoval,
                        maximumIterations,
                        maximumIterationsWithoutImprovement,
                        initialTemperature,
                        minimumTemperature,
                        coolingFactor,
                        reactionFactor
                ),

                maxTramoKm,

                algoritmo,

                ObjetivoPlanificacion.parse(
                        objetivo
                )
        );
    }

    /*
     * =====================================================
     * CONSTRUCTORES PARA TESTS EXISTENTES
     * =====================================================
     */

PlanificadorSaService(
        FuenteDatosOperativos fuente
) {

    this(
            fuente,
            CONFIG_POR_DEFECTO,
            ALNS_CONFIG_POR_DEFECTO,
            0.0,
            "SA",
            ObjetivoPlanificacion.SERVICE_THEN_COST
    );
}

/**
 * Constructor histórico mantenido para compatibilidad
 * con tests y subclases existentes.
 */
PlanificadorSaService(
        FuenteDatosOperativos fuente,
        double maxTramoKm
) {

    this(
            fuente,
            CONFIG_POR_DEFECTO,
            ALNS_CONFIG_POR_DEFECTO,
            maxTramoKm,
            "SA",
            ObjetivoPlanificacion.SERVICE_THEN_COST
    );
}

PlanificadorSaService(
        FuenteDatosOperativos fuente,
        AnnealingConfig config
) {

    this(
            fuente,
            config,
            ALNS_CONFIG_POR_DEFECTO,
            0.0,
            "SA",
            ObjetivoPlanificacion.SERVICE_THEN_COST
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
            ALNS_CONFIG_POR_DEFECTO,
            maxTramoKm,
            "SA",
            ObjetivoPlanificacion.SERVICE_THEN_COST
    );
}

    /*
     * =====================================================
     * CONSTRUCTOR CENTRAL
     * =====================================================
     */

    private PlanificadorSaService(
            FuenteDatosOperativos fuente,
            AnnealingConfig configSa,
            AlnsConfig configAlns,
            double maxTramoKm,
            String algoritmo,
            ObjetivoPlanificacion objetivo
    ) {

        this.fuente =
                Objects.requireNonNull(
                        fuente,
                        "fuente es requerida"
                );

        this.configSa =
                Objects.requireNonNull(
                        configSa,
                        "config SA es requerida"
                );

        this.configAlns =
                Objects.requireNonNull(
                        configAlns,
                        "config ALNS es requerida"
                );

        this.algoritmo =
                normalizarAlgoritmo(
                        algoritmo
                );

        this.objetivo =
                Objects.requireNonNull(
                        objetivo,
                        "objetivo es requerido"
                );

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
                new OperationalAlnsObjectivePlanner(
                        evaluador
                );
    }

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

    /**
     * Algoritmo seleccionado en configuración.
     *
     * Retorna:
     *
     *     SA
     *
     * o:
     *
     *     ALNS
     */
    public String algoritmoConfigurado() {

    return algoritmo;
}

/**
 * Alias mantenido por compatibilidad con las pruebas
 * y componentes existentes.
 */
public AlgoritmoPlanificacion algoritmo() {

    return AlgoritmoPlanificacion.valueOf(
            algoritmo
    );
}

    /**
     * Objetivo configurado.
     *
     * Actualmente afecta ALNS.
     *
     * SA todavía mantiene su objetivo tradicional hasta que
     * Jorge porte esta misma política al algoritmo.
     */
    public ObjetivoPlanificacion objetivoConfigurado() {

        return objetivo;
    }

    public boolean usaAlns() {

        return "ALNS".equals(
                algoritmo
        );
    }

    /*
     * =====================================================
     * EVALUADOR
     * =====================================================
     */

    /**
     * Evaluador común utilizado tanto por SA como por ALNS.
     *
     * ReplanificacionService también lo reutiliza para
     * validar rutas conservadas.
     */
    OperationalPlanEvaluator evaluador() {

        return evaluador;
    }

    /*
     * =====================================================
     * PLANIFICACIÓN
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
                solicitud.configuracionSa()
                        != null

                        ? solicitud.configuracionSa()
                        : configSa;

        EjecucionSa ejecucion =
                ejecutarSa(
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
     * EJECUCIÓN
     * =====================================================
     *
     * El nombre ejecutarSa se conserva porque
     * ReplanificacionSaService ya depende de este método.
     *
     * Internamente puede ejecutar SA o ALNS.
     */

    record EjecucionSa(
            ResultadoPlanificacion resultado,
            long duracionMs
    ) {
    }

    EjecucionSa ejecutarSa(
            OperationalSnapshot snapshot,
            List<Order> pedidos,
            List<RoadBlock> bloqueos,
            long semilla,
            long presupuestoMs
    ) {

        return ejecutarSa(
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

        long inicio =
                System.nanoTime();

        ResultadoPlanificacion resultado;

        if (
                usaAlns()
        ) {

            /*
             * =================================================
             * ALNS
             * =================================================
             *
             * SERVICE_MAX:
             *   ALNS + rescate orientado a servicio.
             *
             * SERVICE_THEN_COST:
             *   comportamiento ALNS normal.
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
                            presupuestoMs,
                            objetivo
                    );

        } else {

            /*
             * =================================================
             * SA
             * =================================================
             *
             * Por ahora SA conserva su comportamiento actual.
             *
             * Más adelante utilizará el mismo
             * ObjetivoPlanificacion para la comparación formal
             * SA vs ALNS.
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
                        1_000_000;

        return new EjecucionSa(
                resultado,
                duracionMs
        );
    }

    /*
     * =====================================================
     * SOLICITUD
     * =====================================================
     */

    static long semillaDe(
            SolicitudPlanificacion solicitud
    ) {

        return solicitud.semilla()
                        != null

                ? solicitud.semilla()

                : ThreadLocalRandom
                        .current()
                        .nextLong();
    }

    static long presupuestoDe(
            SolicitudPlanificacion solicitud
    ) {

        return solicitud.presupuestoMs()
                        != null

                ? solicitud.presupuestoMs()

                : PRESUPUESTO_POR_DEFECTO_MS;
    }

    /*
     * =====================================================
     * SNAPSHOT
     * =====================================================
     */

    /**
     * Para una planificación inicial todos los vehículos
     * disponibles se colocan en el almacén central.
     *
     * En replanificación, ReplanificacionService construye
     * el snapshot correspondiente al estado operativo real.
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

        Map<String, VehicleOperationalState> estados =
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

    /*
     * =====================================================
     * VALIDACIÓN CONFIG
     * =====================================================
     */

    private static String normalizarAlgoritmo(
            String algoritmo
    ) {

        if (
                algoritmo == null
                ||
                algoritmo.isBlank()
        ) {

            return "SA";
        }

        String normalized =
                algoritmo
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        return switch (
                normalized
        ) {

            case "SA",
                 "SIMULATED_ANNEALING" ->

                    "SA";

            case "ALNS" ->

                    "ALNS";

            default ->

                    throw new IllegalArgumentException(
                            "Algoritmo de planificación desconocido: "
                                    +
                                    algoritmo
                    );
        };
    }
}