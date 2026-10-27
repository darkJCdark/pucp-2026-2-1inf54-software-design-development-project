package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Planificador basado en Simulated Annealing. Arma el {@link OperationalSnapshot} con los datos de
 * {@link FuenteDatosOperativos}, ejecuta el SA con la semilla indicada y devuelve el {@link PlanResponse}.
 */
@Service
public class PlanificadorSaService implements PlanificadorService {

    /** Parámetros del SA de la experimentación numérica (config/formal-aprobado.properties, rama feature/expnumerica). */
    static final AnnealingConfig CONFIG_POR_DEFECTO = new AnnealingConfig(1000.0, 1.0, 0.95, 50, 1_000_000, 1_000_000);

    public static AnnealingConfig configuracionPorDefecto() {
        return CONFIG_POR_DEFECTO;
    }

    public static AnnealingConfig configuracionConParametros(double temperaturaInicial, double temperaturaMinima,
                                                              double factorEnfriamiento, int iteracionesPorTemperatura) {
        return new AnnealingConfig(temperaturaInicial, temperaturaMinima, factorEnfriamiento,
                iteracionesPorTemperatura, CONFIG_POR_DEFECTO.maximumIterations(),
                CONFIG_POR_DEFECTO.maximumIterationsWithoutImprovement());
    }

    /**
     * Tope por ciclo cuando la solicitud no trae uno: el presupuesto de las corridas smoke/readiness del
     * experimento. El valor definitivo para la Simulación 5D lo envía quien controla el reloj.
     */
    static final long PRESUPUESTO_POR_DEFECTO_MS = 1_500L;

    private final FuenteDatosOperativos fuente;
    private final AnnealingConfig config;
    private final OperationalPlanEvaluator evaluador;
    private final OperationalSimulatedAnnealingPlanner planificador;
    private final PlanResponseMapper mapper = new PlanResponseMapper();

    /** @param maxTramoKm límite de distancia por tramo; 0 lo deshabilita (regla final del caso, maxLegKm = 0). */
    @Autowired
    public PlanificadorSaService(FuenteDatosOperativos fuente,
                                 @Value("${paqrap.planificacion.max-tramo-km:0}") double maxTramoKm) {
        this(fuente, CONFIG_POR_DEFECTO, maxTramoKm);
    }

    PlanificadorSaService(FuenteDatosOperativos fuente) {
        this(fuente, CONFIG_POR_DEFECTO, 0);
    }

    PlanificadorSaService(FuenteDatosOperativos fuente, AnnealingConfig config) {
        this(fuente, config, 0);
    }

    PlanificadorSaService(FuenteDatosOperativos fuente, AnnealingConfig config, double maxTramoKm) {
        this.fuente = Objects.requireNonNull(fuente, "fuente es requerida");
        this.config = Objects.requireNonNull(config, "config es requerida");
        this.evaluador = new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()), maxTramoKm);
        this.planificador = new OperationalSimulatedAnnealingPlanner(evaluador);
    }

    /** Evaluador con el que se valida el SA; la replanificación lo reutiliza para las rutas conservadas. */
    OperationalPlanEvaluator evaluador() {
        return evaluador;
    }

    @Override
    public PlanResponse planificar(SolicitudPlanificacion solicitud) {
        Objects.requireNonNull(solicitud, "solicitud es requerida");
        OperationalSnapshot snapshot = construirSnapshot(solicitud.horaPlanificacion());
        long semilla = semillaDe(solicitud);
        long presupuestoMs = presupuestoDe(solicitud);

        AnnealingConfig configuracion = solicitud.configuracionSa() != null
                ? solicitud.configuracionSa() : config;
        EjecucionSa ejecucion = ejecutarSa(snapshot, solicitud.pedidosPendientes(), solicitud.bloqueosActivos(),
                configuracion, semilla, presupuestoMs);

        return mapper.aResponse(solicitud.modo(), solicitud.horaPlanificacion(), ejecucion.resultado(), semilla,
                ejecucion.duracionMs(), presupuestoMs);
    }

    /** Resultado del SA y su tiempo de cómputo; lo reutiliza la replanificación con su propio snapshot. */
    record EjecucionSa(ResultadoPlanificacion resultado, long duracionMs) {
    }

    EjecucionSa ejecutarSa(OperationalSnapshot snapshot, List<Order> pedidos, List<RoadBlock> bloqueos,
                           long semilla, long presupuestoMs) {
        return ejecutarSa(snapshot, pedidos, bloqueos, config, semilla, presupuestoMs);
    }

    EjecucionSa ejecutarSa(OperationalSnapshot snapshot, List<Order> pedidos, List<RoadBlock> bloqueos,
                           AnnealingConfig configuracion, long semilla, long presupuestoMs) {
        long inicio = System.nanoTime();
        ResultadoPlanificacion resultado = planificador.planificar(snapshot, pedidos, bloqueos, configuracion,
                new Random(semilla), presupuestoMs);
        return new EjecucionSa(resultado, (System.nanoTime() - inicio) / 1_000_000);
    }

    static long semillaDe(SolicitudPlanificacion solicitud) {
        return solicitud.semilla() != null ? solicitud.semilla() : ThreadLocalRandom.current().nextLong();
    }

    static long presupuestoDe(SolicitudPlanificacion solicitud) {
        return solicitud.presupuestoMs() != null ? solicitud.presupuestoMs() : PRESUPUESTO_POR_DEFECTO_MS;
    }

    /** Todos los vehículos parten disponibles desde el almacén central a la hora de planificación. */
    private OperationalSnapshot construirSnapshot(Instant hora) {
        Warehouse central = fuente.almacenes().stream()
                .filter(Warehouse::isCentral)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("La fuente de datos no define un almacén central"));
        Map<String, VehicleOperationalState> estados = new LinkedHashMap<>();
        for (Vehicle vehiculo : fuente.flota()) {
            estados.put(vehiculo.id(),
                    new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), hora));
        }
        return new OperationalSnapshot(hora, fuente.perfilFlota(), InventorySnapshot.from(fuente.almacenes()), estados,
                fuente.calendarioMantenimiento(), new ShiftSchedule(ShiftSchedule.DEFAULT_ZONE), fuente.averias());
    }
}
