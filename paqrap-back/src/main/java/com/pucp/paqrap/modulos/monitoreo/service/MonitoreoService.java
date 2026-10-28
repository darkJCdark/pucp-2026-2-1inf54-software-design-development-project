package com.pucp.paqrap.modulos.monitoreo.service;

import com.pucp.paqrap.modulos.almacenes.service.AlmacenService;
import com.pucp.paqrap.modulos.almacenes.service.InventarioService;
import com.pucp.paqrap.modulos.escenarios.dto.EscenarioResponse;
import com.pucp.paqrap.modulos.escenarios.service.EscenarioService;
import com.pucp.paqrap.modulos.escenarios.service.DetectorColapso;
import com.pucp.paqrap.modulos.escenarios.service.EjecutorPlanService;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.dto.MantenimientoResponse;
import com.pucp.paqrap.modulos.flota.service.FlotaService;
import com.pucp.paqrap.modulos.flota.service.MantenimientoService;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.incidencias.dto.AveriaResponse;
import com.pucp.paqrap.modulos.incidencias.service.AveriaService;
import com.pucp.paqrap.modulos.monitoreo.dto.MonitoreoResponse;
import com.pucp.paqrap.modulos.pedidos.service.PedidoService;
import com.pucp.paqrap.modulos.redvial.service.BloqueoService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Junta, en el instante simulado de un escenario, la información de los demás módulos para el mapa (CU-12). */
@Service
public class MonitoreoService {

    /** Una avería mayor inhabilita como máximo unos 3 días; las anteriores ya no pueden estar en curso. */
    private static final Duration ALCANCE_AVERIAS = Duration.ofDays(4);
    private static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;

    private final EscenarioService escenarioService;
    private final PedidoService pedidoService;
    private final FlotaService flotaService;
    private final AlmacenService almacenService;
    private final InventarioService inventarioService;
    private final BloqueoService bloqueoService;
    private final AveriaService averiaService;
    private final EjecutorPlanService ejecutorPlan;
    private final DetectorColapso detectorColapso;
    private final MantenimientoService mantenimientoService;

    public MonitoreoService(EscenarioService escenarioService, PedidoService pedidoService, FlotaService flotaService,
                            AlmacenService almacenService, InventarioService inventarioService,
                            BloqueoService bloqueoService, AveriaService averiaService,
                            EjecutorPlanService ejecutorPlan, DetectorColapso detectorColapso,
                            MantenimientoService mantenimientoService) {
        this.mantenimientoService = mantenimientoService;
        this.escenarioService = escenarioService;
        this.pedidoService = pedidoService;
        this.flotaService = flotaService;
        this.almacenService = almacenService;
        this.inventarioService = inventarioService;
        this.bloqueoService = bloqueoService;
        this.averiaService = averiaService;
        this.ejecutorPlan = ejecutorPlan;
        this.detectorColapso = detectorColapso;
    }

    public MonitoreoResponse estado(long ejecucionId) {
        EscenarioResponse escenario = escenarioService.obtener(ejecucionId);
        Instant instante = Optional.ofNullable(escenario.instanteSimulado())
                .or(() -> Optional.ofNullable(escenario.finSimulado()))
                .orElse(escenario.inicioSimulado());

        List<AveriaResponse> averiasActivas = averiaService
                .listar(null, null, null, instante.minus(ALCANCE_AVERIAS), instante.plusNanos(1)).stream()
                .filter(averia -> averia.indisponibleHasta().isAfter(instante))
                .filter(averia -> averia.ejecucionId() == null || averia.ejecucionId() == ejecucionId)
                .toList();
        Map<String, Instant> averiadoHasta = averiasActivas.stream()
                .collect(Collectors.toMap(AveriaResponse::vehiculoId, AveriaResponse::indisponibleHasta,
                        (a, b) -> a.isAfter(b) ? a : b));

        // Un mantenimiento inhabilita el día local completo (MaintenanceDay del dominio).
        LocalDate hoy = instante.atZone(ZONA).toLocalDate();
        Instant finMantenimiento = hoy.plusDays(1).atStartOfDay(ZONA).toInstant();
        Set<String> enMantenimiento = mantenimientoService.listar(null, hoy, hoy).stream()
                .map(MantenimientoResponse::vehiculoId)
                .collect(Collectors.toSet());

        List<MonitoreoResponse.Vehiculo> vehiculos = flotaService.listarVehiculos(null, null).stream()
                .map(v -> {
                    MonitoreoResponse.MotivoDetencion motivo = null;
                    Instant hasta = null;
                    Optional<Instant> refrigerio;
                    if (averiadoHasta.containsKey(v.id())) {
                        motivo = MonitoreoResponse.MotivoDetencion.AVERIA;
                        hasta = averiadoHasta.get(v.id());
                    } else if (enMantenimiento.contains(v.id())) {
                        motivo = MonitoreoResponse.MotivoDetencion.MANTENIMIENTO;
                        hasta = finMantenimiento;
                    } else if ((refrigerio = ejecutorPlan.refrigerioHasta(ejecucionId, v.id(), instante)).isPresent()) {
                        motivo = MonitoreoResponse.MotivoDetencion.REFRIGERIO;
                        hasta = refrigerio.get();
                    }
                    return new MonitoreoResponse.Vehiculo(v.id(), v.tipo(), v.estado(), v.x(), v.y(),
                            v.cargaActual(), averiadoHasta.get(v.id()), motivo, hasta);
                })
                .toList();

        Map<String, Optional<Integer>> stocks = inventarioService.stocks(ejecucionId, instante);
        List<MonitoreoResponse.Almacen> almacenes = almacenService.listar().stream()
                .map(a -> new MonitoreoResponse.Almacen(a.id(), a.tipo(), a.x(), a.y(),
                        stocks.getOrDefault(a.id(), Optional.empty()).orElse(null), a.capacidad()))
                .toList();

        Map<VehicleType, Long> enRuta = new EnumMap<>(VehicleType.class);
        for (VehicleType tipo : VehicleType.values()) {
            enRuta.put(tipo, vehiculos.stream()
                    .filter(v -> v.tipo() == tipo && v.estado() == VehicleOperationalStatus.IN_ROUTE)
                    .count());
        }
        EjecutorPlanService.Metricas metricas = ejecutorPlan.metricas(ejecucionId)
                .orElse(new EjecutorPlanService.Metricas(0, 0, 0, 0, 0));
        MonitoreoResponse.Ejecucion ejecucion = new MonitoreoResponse.Ejecucion(metricas.pedidosEntregados(),
                metricas.entregadosATiempo(), metricas.paquetesEntregados(), metricas.distanciaKm(), metricas.costo(),
                enRuta);

        return new MonitoreoResponse(escenario, instante, pedidoService.resumen(escenario.inicioSimulado(), instante),
                vehiculos, almacenes, bloqueoService.listar(null, null, instante), averiasActivas, ejecucion,
                detectorColapso.colapso(ejecucionId).orElse(null));
    }
}
