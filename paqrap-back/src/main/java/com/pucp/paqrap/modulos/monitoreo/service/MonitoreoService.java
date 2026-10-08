package com.pucp.paqrap.modulos.monitoreo.service;

import com.pucp.paqrap.modulos.almacenes.service.AlmacenService;
import com.pucp.paqrap.modulos.almacenes.service.InventarioService;
import com.pucp.paqrap.modulos.escenarios.dto.EscenarioResponse;
import com.pucp.paqrap.modulos.escenarios.service.EscenarioService;
import com.pucp.paqrap.modulos.flota.service.FlotaService;
import com.pucp.paqrap.modulos.incidencias.dto.AveriaResponse;
import com.pucp.paqrap.modulos.incidencias.service.AveriaService;
import com.pucp.paqrap.modulos.monitoreo.dto.MonitoreoResponse;
import com.pucp.paqrap.modulos.pedidos.service.PedidoService;
import com.pucp.paqrap.modulos.redvial.service.BloqueoService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Junta, en el instante simulado de un escenario, la información de los demás módulos para el mapa (CU-12). */
@Service
public class MonitoreoService {

    /** Una avería mayor inhabilita como máximo unos 3 días; las anteriores ya no pueden estar en curso. */
    private static final Duration ALCANCE_AVERIAS = Duration.ofDays(4);

    private final EscenarioService escenarioService;
    private final PedidoService pedidoService;
    private final FlotaService flotaService;
    private final AlmacenService almacenService;
    private final InventarioService inventarioService;
    private final BloqueoService bloqueoService;
    private final AveriaService averiaService;

    public MonitoreoService(EscenarioService escenarioService, PedidoService pedidoService, FlotaService flotaService,
                            AlmacenService almacenService, InventarioService inventarioService,
                            BloqueoService bloqueoService, AveriaService averiaService) {
        this.escenarioService = escenarioService;
        this.pedidoService = pedidoService;
        this.flotaService = flotaService;
        this.almacenService = almacenService;
        this.inventarioService = inventarioService;
        this.bloqueoService = bloqueoService;
        this.averiaService = averiaService;
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

        List<MonitoreoResponse.Vehiculo> vehiculos = flotaService.listarVehiculos(null, null).stream()
                .map(v -> new MonitoreoResponse.Vehiculo(v.id(), v.tipo(), v.estado(), v.x(), v.y(), v.cargaActual(),
                        averiadoHasta.get(v.id())))
                .toList();

        Map<String, Optional<Integer>> stocks = inventarioService.stocks(ejecucionId, instante);
        List<MonitoreoResponse.Almacen> almacenes = almacenService.listar().stream()
                .map(a -> new MonitoreoResponse.Almacen(a.id(), a.tipo(), a.x(), a.y(),
                        stocks.getOrDefault(a.id(), Optional.empty()).orElse(null), a.capacidad()))
                .toList();

        return new MonitoreoResponse(escenario, instante, pedidoService.resumen(escenario.inicioSimulado(), instante),
                vehiculos, almacenes, bloqueoService.listar(null, null, instante), averiasActivas);
    }
}
