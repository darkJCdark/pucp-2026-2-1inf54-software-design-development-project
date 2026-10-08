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

    /**
     * Tope por ciclo cuando la solicitud no trae uno: el presupuesto de las corridas smoke/readiness del
     * experimento. El valor definitivo para la Simulación 5D lo envía quien controla el reloj.
     */
    static final long PRESUPUESTO_POR_DEFECTO_MS = 1_500L;

    private final FuenteDatosOperativos fuente;
    private final AnnealingConfig config;
    private final OperationalSimulatedAnnealingPlanner planificador;
    private final PlanResponseMapper mapper = new PlanResponseMapper();

    @Autowired
    public PlanificadorSaService(FuenteDatosOperativos fuente) {
        this(fuente, CONFIG_POR_DEFECTO);
    }

    PlanificadorSaService(FuenteDatosOperativos fuente, AnnealingConfig config) {
        this.fuente = Objects.requireNonNull(fuente, "fuente es requerida");
        this.config = Objects.requireNonNull(config, "config es requerida");
        this.planificador = new OperationalSimulatedAnnealingPlanner(
                new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork())));
    }

    @Override
    public PlanResponse planificar(SolicitudPlanificacion solicitud) {
        Objects.requireNonNull(solicitud, "solicitud es requerida");
        OperationalSnapshot snapshot = construirSnapshot(solicitud.horaPlanificacion());
        long semilla = semillaDe(solicitud);
        long presupuestoMs = presupuestoDe(solicitud);

        EjecucionSa ejecucion = ejecutarSa(snapshot, solicitud.pedidosPendientes(), solicitud.bloqueosActivos(),
                semilla, presupuestoMs);

        return mapper.aResponse(solicitud.modo(), solicitud.horaPlanificacion(), ejecucion.resultado(), semilla,
                ejecucion.duracionMs(), presupuestoMs);
    }

    /** Resultado del SA y su tiempo de cómputo; lo reutiliza la replanificación con su propio snapshot. */
    record EjecucionSa(ResultadoPlanificacion resultado, long duracionMs) {
    }

    EjecucionSa ejecutarSa(OperationalSnapshot snapshot, List<Order> pedidos, List<RoadBlock> bloqueos,
                           long semilla, long presupuestoMs) {
        long inicio = System.nanoTime();
        ResultadoPlanificacion resultado = planificador.planificar(snapshot, pedidos, bloqueos, config,
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
