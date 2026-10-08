package com.pucp.paqrap.modulos.flota.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.flota.dto.MantenimientoRequest;
import com.pucp.paqrap.modulos.flota.dto.MantenimientoResponse;
import com.pucp.paqrap.modulos.flota.persistence.MaintenanceDayEntity;
import com.pucp.paqrap.modulos.flota.repository.MaintenanceDayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Programación del mantenimiento preventivo: un vehículo no se planifica el día que tiene mantenimiento. */
@Service
@Transactional(readOnly = true)
public class MantenimientoService {

    private final MaintenanceDayRepository mantenimientoRepository;
    private final FlotaService flotaService;

    public MantenimientoService(MaintenanceDayRepository mantenimientoRepository, FlotaService flotaService) {
        this.mantenimientoRepository = mantenimientoRepository;
        this.flotaService = flotaService;
    }

    public List<MantenimientoResponse> listar(String vehiculoId, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException("'hasta' no puede ser anterior a 'desde'");
        }
        return mantenimientoRepository.buscar(vehiculoId, desde, hasta).stream()
                .map(MantenimientoResponse::de)
                .toList();
    }

    @Transactional
    public MantenimientoResponse programar(MantenimientoRequest request) {
        if (!flotaService.existeVehiculo(request.vehiculoId())) {
            throw new RecursoNoEncontradoException("Vehículo", request.vehiculoId());
        }
        MaintenanceDayEntity.Id id = new MaintenanceDayEntity.Id(request.vehiculoId(), request.fecha());
        if (mantenimientoRepository.existsById(id)) {
            throw new ReglaNegocioException("El vehículo " + request.vehiculoId()
                    + " ya tiene mantenimiento programado el " + request.fecha());
        }
        MaintenanceDayEntity guardado = mantenimientoRepository.save(
                new MaintenanceDayEntity(request.vehiculoId(), request.fecha()));
        return MantenimientoResponse.de(guardado);
    }

    @Transactional
    public void cancelar(String vehiculoId, LocalDate fecha) {
        MaintenanceDayEntity.Id id = new MaintenanceDayEntity.Id(vehiculoId, fecha);
        if (!mantenimientoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Mantenimiento", vehiculoId + " " + fecha);
        }
        mantenimientoRepository.deleteById(id);
    }
}
