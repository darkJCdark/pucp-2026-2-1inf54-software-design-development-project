package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.almacenes.service.InventarioService;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownEventEntity;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.incidencias.service.BreakdownAvailabilityCalculator;
import com.pucp.paqrap.modulos.pedidos.repository.OrderRepository;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.RoadBlock;
import com.pucp.paqrap.modulos.redvial.persistence.RoadBlockEntity;
import com.pucp.paqrap.modulos.redvial.repository.RoadBlockRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Eventos que ocurren dentro de la simulación al avanzar el reloj de (desde, hasta]: la recarga diaria de los
 * almacenes intermedios (23:59:59 en Lima, igual que el dominio de SA), el inicio de bloqueos viales, las averías
 * de la ejecución que ocurren en el intervalo y la llegada de pedidos (su hora de registro).
 */
@Component
public class EventosSimulacion {

    private static final ZoneId ZONA = ShiftSchedule.DEFAULT_ZONE;
    private static final LocalTime HORA_RECARGA = LocalTime.of(23, 59, 59);

    private final InventarioService inventarioService;
    private final RoadBlockRepository bloqueoRepository;
    private final BreakdownEventRepository averiaRepository;
    private final OrderRepository pedidoRepository;
    private final BreakdownAvailabilityCalculator calculadoraAverias =
            new BreakdownAvailabilityCalculator(ShiftSchedule.defaultSchedule());

    public EventosSimulacion(InventarioService inventarioService, RoadBlockRepository bloqueoRepository,
                             BreakdownEventRepository averiaRepository, OrderRepository pedidoRepository) {
        this.inventarioService = inventarioService;
        this.bloqueoRepository = bloqueoRepository;
        this.averiaRepository = averiaRepository;
        this.pedidoRepository = pedidoRepository;
    }

    /**
     * Lo que pasó en el intervalo. El inicio y el fin efectivo de una avería son eventos distintos:
     * el primero retira la unidad y el segundo la vuelve a incorporar inmediatamente al siguiente plan.
     */
    public record Resumen(int recargas, List<RoadBlock> bloqueosIniciados, List<Long> bloqueosIniciadosIds,
                          List<RoadBlock> bloqueosTerminados, List<Long> bloqueosTerminadosIds,
                          List<BreakdownEvent> averias, List<Long> averiasIds,
                          List<BreakdownEvent> averiasTerminadas, List<Long> averiasTerminadasIds,
                          List<String> pedidosLlegadosIds) {
        public Resumen {
            bloqueosIniciados = List.copyOf(bloqueosIniciados);
            bloqueosIniciadosIds = List.copyOf(bloqueosIniciadosIds);
            bloqueosTerminados = List.copyOf(bloqueosTerminados);
            bloqueosTerminadosIds = List.copyOf(bloqueosTerminadosIds);
            averias = List.copyOf(averias);
            averiasIds = List.copyOf(averiasIds);
            averiasTerminadas = List.copyOf(averiasTerminadas);
            averiasTerminadasIds = List.copyOf(averiasTerminadasIds);
            pedidosLlegadosIds = List.copyOf(pedidosLlegadosIds);
            if (bloqueosIniciados.size() != bloqueosIniciadosIds.size()) {
                throw new IllegalArgumentException("Cada bloqueo iniciado debe conservar su identificador");
            }
            if (bloqueosTerminados.size() != bloqueosTerminadosIds.size()) {
                throw new IllegalArgumentException("Cada bloqueo terminado debe conservar su identificador");
            }
            if (averias.size() != averiasIds.size()) {
                throw new IllegalArgumentException("Cada avería debe conservar su identificador");
            }
            if (averiasTerminadas.size() != averiasTerminadasIds.size()) {
                throw new IllegalArgumentException("Cada avería terminada debe conservar su identificador");
            }
        }
    }

    public Resumen procesar(long ejecucionId, Instant desde, Instant hasta) {
        int recargas = 0;
        for (LocalDate dia = desde.atZone(ZONA).toLocalDate(); ; dia = dia.plusDays(1)) {
            Instant recarga = dia.atTime(HORA_RECARGA).atZone(ZONA).toInstant();
            if (recarga.isAfter(hasta)) {
                break;
            }
            if (recarga.isAfter(desde)) {
                inventarioService.recargarIntermedios(ejecucionId, recarga);
                recargas++;
            }
        }
        List<RoadBlockEntity> bloqueosEntidades = bloqueoRepository.queEmpiezanEntre(desde, hasta);
        List<RoadBlock> bloqueos = bloqueosEntidades.stream()
                .map(RoadBlockEntity::toDomain)
                .toList();
        List<RoadBlockEntity> bloqueosTerminadosEntidades = bloqueoRepository.queTerminanEntre(desde, hasta);
        List<RoadBlock> bloqueosTerminados = bloqueosTerminadosEntidades.stream()
                .map(RoadBlockEntity::toDomain)
                .toList();
        // buscar filtra [desde, hasta): se corre un nanosegundo para tomar (desde, hasta].
        List<BreakdownEventEntity> averiasEntidades = averiaRepository
                .buscar(null, null, ejecucionId, desde.plusNanos(1), hasta.plusNanos(1));
        List<BreakdownEvent> averias = averiasEntidades.stream()
                .map(BreakdownEventEntity::toDomain)
                .toList();

        /*
         * unavailableUntil no está materializado en la tabla: se deriva con las mismas reglas del dominio.
         * Se toma (desde, hasta] para que cada finalización se procese una sola vez.
         */
        List<BreakdownEventEntity> averiasTerminadasEntidades = averiaRepository.deLaEjecucion(null, ejecucionId).stream()
                .filter(entity -> {
                    Instant disponibleDesde = calculadoraAverias.resolve(entity.toDomain()).availableFrom();
                    return disponibleDesde.isAfter(desde) && !disponibleDesde.isAfter(hasta);
                })
                .toList();
        List<BreakdownEvent> averiasTerminadas = averiasTerminadasEntidades.stream()
                .map(BreakdownEventEntity::toDomain)
                .toList();

        List<String> pedidos = pedidoRepository.idsRegistradosEntre(desde, hasta);
        return new Resumen(recargas, bloqueos,
                bloqueosEntidades.stream().map(RoadBlockEntity::getRoadBlockId).toList(), bloqueosTerminados,
                bloqueosTerminadosEntidades.stream().map(RoadBlockEntity::getRoadBlockId).toList(), averias,
                averiasEntidades.stream().map(BreakdownEventEntity::getBreakdownId).toList(), averiasTerminadas,
                averiasTerminadasEntidades.stream().map(BreakdownEventEntity::getBreakdownId).toList(), pedidos);
    }
}
