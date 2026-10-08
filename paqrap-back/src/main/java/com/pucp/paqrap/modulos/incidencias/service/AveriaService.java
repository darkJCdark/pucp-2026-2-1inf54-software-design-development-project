package com.pucp.paqrap.modulos.incidencias.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.flota.dto.VehiculoResponse;
import com.pucp.paqrap.modulos.flota.service.FlotaService;
import com.pucp.paqrap.modulos.incidencias.dto.AveriaResponse;
import com.pucp.paqrap.modulos.incidencias.dto.RegistrarAveriaRequest;
import com.pucp.paqrap.modulos.incidencias.entity.AveriaRegistrada;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownResolution;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownType;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownEventEntity;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Registro y consulta de averías (CU-06, CU-08). Solo se guarda el evento: el planificador deja fuera a la unidad
 * mientras dure la avería a partir de los eventos, por lo que el estado del vehículo no se modifica aquí.
 */
@Service
@Transactional(readOnly = true)
public class AveriaService {

    private final BreakdownEventRepository averiaRepository;
    private final FlotaService flotaService;
    private final ApplicationEventPublisher eventos;
    private final BreakdownAvailabilityCalculator calculadora =
            new BreakdownAvailabilityCalculator(ShiftSchedule.defaultSchedule());

    public AveriaService(BreakdownEventRepository averiaRepository, FlotaService flotaService,
                         ApplicationEventPublisher eventos) {
        this.averiaRepository = averiaRepository;
        this.flotaService = flotaService;
        this.eventos = eventos;
    }

    @Transactional
    public AveriaResponse registrar(RegistrarAveriaRequest request) {
        if ((request.x() == null) != (request.y() == null)) {
            throw new IllegalArgumentException("Indique ambas coordenadas 'x' e 'y', o ninguna");
        }
        VehiculoResponse vehiculo = flotaService.obtenerVehiculo(request.vehiculoId());
        Instant ocurridaEn = request.ocurridaEn() != null ? request.ocurridaEn() : Instant.now();
        Location ubicacion = request.x() != null
                ? new Location(request.x(), request.y())
                : new Location(vehiculo.x(), vehiculo.y());

        validarQueNoEsteAveriado(vehiculo.id(), ocurridaEn);

        BreakdownEvent evento = new BreakdownEvent(vehiculo.id(), request.tipo(), ocurridaEn, ubicacion);
        BreakdownEventEntity guardado = averiaRepository.save(new BreakdownEventEntity(request.ejecucionId(), evento));
        if (request.ejecucionId() != null) {
            // El motor de escenarios replanifica (CU-07) cuando esta transacción se confirma.
            eventos.publishEvent(new AveriaRegistrada(request.ejecucionId(), vehiculo.id(), ocurridaEn));
        }
        return AveriaResponse.de(guardado, calculadora.resolve(evento));
    }

    public List<AveriaResponse> listar(String vehiculoId, BreakdownType tipo, Long ejecucionId,
                                       Instant desde, Instant hasta) {
        if (desde != null && hasta != null && !hasta.isAfter(desde)) {
            throw new IllegalArgumentException("'hasta' debe ser posterior a 'desde'");
        }
        return averiaRepository.buscar(vehiculoId, tipo, ejecucionId, desde, hasta).stream()
                .map(this::aRespuesta)
                .toList();
    }

    public AveriaResponse obtener(Long id) {
        return averiaRepository.findById(id)
                .map(this::aRespuesta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Avería", id));
    }

    /** Una unidad que sigue inhabilitada por una avería previa no puede volver a averiarse. */
    private void validarQueNoEsteAveriado(String vehiculoId, Instant ocurridaEn) {
        averiaRepository.findFirstByVehicleIdAndOccurredAtLessThanEqualOrderByOccurredAtDesc(vehiculoId, ocurridaEn)
                .ifPresent(anterior -> {
                    BreakdownResolution resolucion = calculadora.resolve(anterior.toDomain());
                    if (ocurridaEn.isBefore(resolucion.unavailableUntil())) {
                        throw new ReglaNegocioException("El vehículo " + vehiculoId + " sigue inhabilitado por la avería "
                                + anterior.getBreakdownId() + " hasta " + resolucion.unavailableUntil());
                    }
                });
    }

    private AveriaResponse aRespuesta(BreakdownEventEntity entity) {
        return AveriaResponse.de(entity, calculadora.resolve(entity.toDomain()));
    }
}
