package com.pucp.paqrap.modulos.monitoreo.service;

import com.pucp.paqrap.modulos.almacenes.dto.AlmacenResponse;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseKind;
import com.pucp.paqrap.modulos.almacenes.repository.InventoryMovementRepository;
import com.pucp.paqrap.modulos.almacenes.service.AlmacenService;
import com.pucp.paqrap.modulos.escenarios.dto.EscenarioResponse;
import com.pucp.paqrap.modulos.escenarios.service.EscenarioService;
import com.pucp.paqrap.modulos.flota.dto.VehiculoResponse;
import com.pucp.paqrap.modulos.flota.service.FlotaService;
import com.pucp.paqrap.modulos.monitoreo.dto.AlmacenDetalleResponse;
import com.pucp.paqrap.modulos.monitoreo.dto.VehiculoDetalleResponse;
import com.pucp.paqrap.modulos.planificacion.dto.ParadaResponse;
import com.pucp.paqrap.modulos.planificacion.dto.PlanResponse;
import com.pucp.paqrap.modulos.planificacion.dto.RutaResponse;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.planificacion.service.PlanVigente;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Detalle de una unidad (P-05) y de un almacén (P-07) en el instante simulado de una ejecución. Solo lee: junta la
 * flota, el último plan, los turnos y el kardex de inventario; no modifica nada.
 */
@Service
public class DetalleOperacionService {

    public static final int HORAS_MAXIMAS = 72;

    private static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;
    private static final LocalTime HORA_RECARGA = LocalTime.of(23, 59, 59);

    private final EscenarioService escenarioService;
    private final FlotaService flotaService;
    private final PlanVigente planVigente;
    private final AlmacenService almacenService;
    private final InventoryMovementRepository movimientoRepository;
    private final ShiftSchedule turnos = ShiftSchedule.defaultSchedule();

    public DetalleOperacionService(EscenarioService escenarioService, FlotaService flotaService,
                                   PlanVigente planVigente, AlmacenService almacenService,
                                   InventoryMovementRepository movimientoRepository) {
        this.escenarioService = escenarioService;
        this.flotaService = flotaService;
        this.planVigente = planVigente;
        this.almacenService = almacenService;
        this.movimientoRepository = movimientoRepository;
    }

    public VehiculoDetalleResponse vehiculo(String id, long ejecucionId) {
        EscenarioResponse escenario = escenarioService.obtener(ejecucionId);
        VehiculoResponse vehiculo = flotaService.obtenerVehiculo(id);
        Instant instante = instante(escenario);

        ShiftSchedule.ShiftWindow turno = turnos.shiftAt(instante);
        ShiftSchedule.ShiftWindow alimentacion = turnos.mealWindow(instante);
        int capacidad = flotaService.obtenerTipo(vehiculo.tipo()).capacidadPaquetes();

        String rutaId = null;
        List<VehiculoDetalleResponse.ProximaEntrega> proximas = new ArrayList<>();
        Optional<PlanResponse> plan = planVigente.ultimoPlan(ejecucionId);
        if (plan.isPresent()) {
            for (RutaResponse ruta : plan.get().rutas()) {
                if (!id.equals(ruta.vehiculoId())) {
                    continue;
                }
                List<VehiculoDetalleResponse.ProximaEntrega> deRuta = ruta.paradas().stream()
                        .filter(p -> ParadaResponse.ENTREGA.equals(p.tipo()))
                        .filter(p -> p.llegada() != null && p.llegada().isAfter(instante))
                        .map(p -> new VehiculoDetalleResponse.ProximaEntrega(p.orden(), p.pedidoId(), p.x(), p.y(),
                                p.paquetes(), p.llegada()))
                        .toList();
                if (!deRuta.isEmpty() && rutaId == null) {
                    rutaId = ruta.id();
                }
                proximas.addAll(deRuta);
            }
        }
        proximas.sort(Comparator.comparing(VehiculoDetalleResponse.ProximaEntrega::llegadaPrevista)
                .thenComparingInt(VehiculoDetalleResponse.ProximaEntrega::orden));

        return new VehiculoDetalleResponse(vehiculo.id(), vehiculo.tipo(), vehiculo.estado(), vehiculo.x(),
                vehiculo.y(), vehiculo.cargaActual(), capacidad, vehiculo.disponibleDesde(), instante,
                new VehiculoDetalleResponse.Turno(turno.startsAt(), turno.endsAt()),
                new VehiculoDetalleResponse.Turno(alimentacion.startsAt(), alimentacion.endsAt()),
                rutaId, List.copyOf(proximas));
    }

    public AlmacenDetalleResponse almacen(String id, long ejecucionId, int horas) {
        if (horas < 1 || horas > HORAS_MAXIMAS) {
            throw new IllegalArgumentException("'horas' debe estar entre 1 y " + HORAS_MAXIMAS);
        }
        EscenarioResponse escenario = escenarioService.obtener(ejecucionId);
        AlmacenResponse almacen = almacenService.obtener(id);
        Instant instante = instante(escenario);
        long consumo = movimientoRepository.despachadoEntre(id, ejecucionId, instante.minus(Duration.ofHours(horas)),
                instante);

        if (almacen.tipo() == WarehouseKind.CENTRAL) {
            return new AlmacenDetalleResponse(id, almacen.tipo(), almacen.x(), almacen.y(), instante, null, null, null,
                    horas, consumo, null, null, List.of());
        }

        int stock = stock(almacen, ejecucionId, instante);
        double ocupacion = Math.round(stock * 1000.0 / almacen.capacidad()) / 10.0;
        List<AlmacenDetalleResponse.PuntoStock> historial = new ArrayList<>();
        for (int atras = horas; atras >= 0; atras--) {
            Instant punto = instante.minus(Duration.ofHours(atras));
            if (!punto.isBefore(escenario.inicioSimulado())) {
                historial.add(new AlmacenDetalleResponse.PuntoStock(punto, stock(almacen, ejecucionId, punto)));
            }
        }
        Instant ultimaRecarga = movimientoRepository.ultimaRecarga(id, ejecucionId, instante).orElse(null);
        return new AlmacenDetalleResponse(id, almacen.tipo(), almacen.x(), almacen.y(), instante, stock,
                almacen.capacidad(), ocupacion, horas, consumo, ultimaRecarga, proximaRecarga(instante), historial);
    }

    /** Stock de un intermedio en {@code instante}; mismo cálculo que {@code InventarioService} sobre el kardex. */
    private int stock(AlmacenResponse almacen, long ejecucionId, Instant instante) {
        Optional<Instant> ultimaRecarga = movimientoRepository.ultimaRecarga(almacen.id(), ejecucionId, instante);
        int base = ultimaRecarga.isPresent() ? almacen.capacidad() : almacen.stockInicial();
        long despachado = movimientoRepository.despachadoEntre(almacen.id(), ejecucionId,
                ultimaRecarga.orElse(null), instante);
        return (int) (base - despachado);
    }

    /** Siguiente recarga diaria (23:59:59 en Lima) posterior a {@code instante}. */
    static Instant proximaRecarga(Instant instante) {
        ZonedDateTime candidata = ZonedDateTime.of(instante.atZone(ZONA).toLocalDate(), HORA_RECARGA, ZONA);
        if (!candidata.toInstant().isAfter(instante)) {
            candidata = candidata.plusDays(1);
        }
        return candidata.toInstant();
    }

    /** Mismo criterio que {@code MonitoreoService}: el instante actual si está activa, el final si terminó. */
    private static Instant instante(EscenarioResponse escenario) {
        return Optional.ofNullable(escenario.instanteSimulado())
                .or(() -> Optional.ofNullable(escenario.finSimulado()))
                .orElse(escenario.inicioSimulado());
    }
}
