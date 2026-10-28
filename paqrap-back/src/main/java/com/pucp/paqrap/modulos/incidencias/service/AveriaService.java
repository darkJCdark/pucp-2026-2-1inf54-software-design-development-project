package com.pucp.paqrap.modulos.incidencias.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.flota.dto.VehiculoResponse;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
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
import java.util.Optional;

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
    private final ContextoSimulacion contexto;
    private final BreakdownAvailabilityCalculator calculadora =
            new BreakdownAvailabilityCalculator(ShiftSchedule.defaultSchedule());

    public AveriaService(BreakdownEventRepository averiaRepository, FlotaService flotaService,
                         ApplicationEventPublisher eventos, ContextoSimulacion contexto) {
        this.averiaRepository = averiaRepository;
        this.flotaService = flotaService;
        this.eventos = eventos;
        this.contexto = contexto;
    }

    @Transactional
    public AveriaResponse registrar(RegistrarAveriaRequest request) {
        if ((request.x() == null) != (request.y() == null)) {
            throw new IllegalArgumentException("Indique ambas coordenadas 'x' e 'y', o ninguna");
        }
        VehiculoResponse vehiculo = flotaService.obtenerVehiculo(request.vehiculoId());
        if (vehiculo.estado() == VehicleOperationalStatus.UNAVAILABLE) {
            throw new ReglaNegocioException("El vehículo " + vehiculo.id()
                    + " está fuera de servicio (UNAVAILABLE); no puede registrarse una avería");
        }
        long ejecucionId = ejecucionDe(request);
        Instant ocurridaEn = horaDeLaAveria(ejecucionId, request.ocurridaEn());
        Location ubicacion = request.x() != null
                ? new Location(request.x(), request.y())
                : ubicacionPorDefecto(ejecucionId, vehiculo, ocurridaEn);

        BreakdownEvent evento = new BreakdownEvent(vehiculo.id(), request.tipo(), ocurridaEn, ubicacion);
        validarQueNoSeCruceConOtraAveria(ejecucionId, evento);

        BreakdownEventEntity guardado = averiaRepository.save(new BreakdownEventEntity(ejecucionId, evento));
        // El motor de escenarios replanifica (CU-07) cuando esta transacción se confirma.
        eventos.publishEvent(new AveriaRegistrada(ejecucionId, evento));
        return AveriaResponse.de(guardado, calculadora.resolve(evento));
    }

    /**
     * Una avería solo afecta a un escenario en curso: sin {@code ejecucionId} se usa el único que está en curso, y si no
     * hay ninguno, hay varios o el indicado ya terminó o se detuvo, se rechaza.
     */
    private long ejecucionDe(RegistrarAveriaRequest request) {
        if (request.ejecucionId() != null) {
            if (contexto.instanteSimulado(request.ejecucionId()).isEmpty()) {
                throw new ReglaNegocioException("La ejecución " + request.ejecucionId()
                        + " no está en curso; solo se registran averías en un escenario activo");
            }
            return request.ejecucionId();
        }
        List<Long> activas = contexto.ejecucionesActivas();
        if (activas.isEmpty()) {
            throw new ReglaNegocioException("No hay un escenario en curso; solo se registran averías en un escenario activo");
        }
        if (activas.size() > 1) {
            throw new ReglaNegocioException("Hay varios escenarios en curso " + activas + "; indique 'ejecucionId'");
        }
        return activas.getFirst();
    }

    /**
     * La hora por defecto es la simulada (no la real), y no puede ser anterior a lo que ya se ejecutó del plan: esas
     * entregas y posiciones ya ocurrieron, así que la avería se registra en ese instante.
     */
    private Instant horaDeLaAveria(long ejecucionId, Instant indicada) {
        Instant hora = indicada != null ? indicada : contexto.instanteSimulado(ejecucionId).orElseThrow();
        Optional<Instant> ejecutado = contexto.ejecutadoHasta(ejecucionId);
        return ejecutado.filter(hora::isBefore).orElse(hora);
    }

    /** Sin coordenadas: si va en ruta, donde lo dibuja el mapa; si no, su posición confirmada. */
    private Location ubicacionPorDefecto(long ejecucionId, VehiculoResponse vehiculo, Instant ocurridaEn) {
        return contexto.posicionEstimada(ejecucionId, vehiculo.id(), ocurridaEn)
                .orElse(new Location(vehiculo.x(), vehiculo.y()));
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

    /**
     * Un vehículo inhabilitado no puede volver a averiarse: la nueva avería no puede caer dentro de una ya registrada, ni
     * dejarlo inhabilitado cuando ocurre otra posterior. Solo cuentan las de la misma ejecución, porque cada escenario
     * repite su periodo desde cero.
     */
    private void validarQueNoSeCruceConOtraAveria(long ejecucionId, BreakdownEvent nueva) {
        Instant nuevaHasta = calculadora.resolve(nueva).unavailableUntil();
        for (BreakdownEventEntity registrada : averiaRepository.deLaEjecucion(nueva.vehicleId(), ejecucionId)) {
            BreakdownEvent otra = registrada.toDomain();
            BreakdownResolution resolucion = calculadora.resolve(otra);
            if (!otra.occurredAt().isAfter(nueva.occurredAt())
                    && nueva.occurredAt().isBefore(resolucion.unavailableUntil())) {
                throw new ReglaNegocioException("El vehículo " + nueva.vehicleId() + " sigue inhabilitado por la avería "
                        + registrada.getBreakdownId() + " hasta " + resolucion.unavailableUntil());
            }
            if (otra.occurredAt().isAfter(nueva.occurredAt()) && otra.occurredAt().isBefore(nuevaHasta)) {
                throw new ReglaNegocioException("El vehículo " + nueva.vehicleId() + " ya tiene registrada la avería "
                        + registrada.getBreakdownId() + " a las " + otra.occurredAt()
                        + ", cuando seguiría inhabilitado por esta (hasta " + nuevaHasta + ")");
            }
        }
    }

    private AveriaResponse aRespuesta(BreakdownEventEntity entity) {
        return AveriaResponse.de(entity, calculadora.resolve(entity.toDomain()));
    }
}
