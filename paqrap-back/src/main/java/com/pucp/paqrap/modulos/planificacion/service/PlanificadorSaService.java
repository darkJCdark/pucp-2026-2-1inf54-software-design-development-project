package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.AnnealingConfig;
import com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing.OperationalSimulatedAnnealingPlanner;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.SolicitudPlanificacion;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
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

    /** Parámetros por defecto del servicio. Son valores propuestos, no los de la experimentación. */
    static final AnnealingConfig CONFIG_POR_DEFECTO = new AnnealingConfig(1000.0, 0.1, 0.95, 50, 5000, 500);

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
        long semilla = solicitud.semilla() != null ? solicitud.semilla() : ThreadLocalRandom.current().nextLong();

        long inicio = System.nanoTime();
        ResultadoPlanificacion resultado = planificador.planificar(snapshot, solicitud.pedidosPendientes(),
                solicitud.bloqueosActivos(), config, new Random(semilla));
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;

        return mapper.aResponse(solicitud.modo(), solicitud.horaPlanificacion(), resultado, semilla, duracionMs);
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
