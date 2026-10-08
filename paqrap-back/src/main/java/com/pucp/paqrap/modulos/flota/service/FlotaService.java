package com.pucp.paqrap.modulos.flota.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.flota.dto.ActualizarTipoVehiculoRequest;
import com.pucp.paqrap.modulos.flota.dto.TipoVehiculoResponse;
import com.pucp.paqrap.modulos.flota.dto.VehiculoResponse;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Consulta y configuración de los tipos de vehículo y de las unidades de la flota. */
@Service
@Transactional(readOnly = true)
public class FlotaService {

    private final VehicleTypeParametersRepository tipoRepository;
    private final VehicleRepository vehiculoRepository;

    public FlotaService(VehicleTypeParametersRepository tipoRepository, VehicleRepository vehiculoRepository) {
        this.tipoRepository = tipoRepository;
        this.vehiculoRepository = vehiculoRepository;
    }

    public List<TipoVehiculoResponse> listarTipos() {
        return tipoRepository.findAll(Sort.by("vehicleType")).stream()
                .map(TipoVehiculoResponse::de)
                .toList();
    }

    public TipoVehiculoResponse obtenerTipo(VehicleType tipo) {
        return TipoVehiculoResponse.de(buscarTipo(tipo));
    }

    @Transactional
    public TipoVehiculoResponse actualizarTipo(VehicleType tipo, ActualizarTipoVehiculoRequest request) {
        VehicleTypeParametersEntity entity = buscarTipo(tipo);
        entity.actualizar(request.capacidadPaquetes(), request.velocidadKmh(), request.costoPorKm());
        return TipoVehiculoResponse.de(entity);
    }

    public List<VehiculoResponse> listarVehiculos(VehicleType tipo, VehicleOperationalStatus estado) {
        return vehiculoRepository.buscar(tipo, estado).stream()
                .map(VehiculoResponse::de)
                .toList();
    }

    public VehiculoResponse obtenerVehiculo(String id) {
        return VehiculoResponse.de(buscarVehiculo(id));
    }

    /** El operario solo alterna entre AVAILABLE y UNAVAILABLE; IN_ROUTE es responsabilidad de la simulación. */
    @Transactional
    public VehiculoResponse cambiarEstado(String id, VehicleOperationalStatus estado) {
        if (estado == VehicleOperationalStatus.IN_ROUTE) {
            throw new ReglaNegocioException("El estado IN_ROUTE lo asigna la simulación, no se registra manualmente");
        }
        VehicleEntity entity = buscarVehiculo(id);
        if (entity.getOperationalStatus() == VehicleOperationalStatus.IN_ROUTE) {
            throw new ReglaNegocioException("El vehículo " + id + " está en ruta; su estado lo controla la simulación");
        }
        entity.setOperationalStatus(estado);
        return VehiculoResponse.de(entity);
    }

    public boolean existeVehiculo(String id) {
        return vehiculoRepository.existsById(id);
    }

    private VehicleTypeParametersEntity buscarTipo(VehicleType tipo) {
        return tipoRepository.findById(tipo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de vehículo", tipo));
    }

    private VehicleEntity buscarVehiculo(String id) {
        return vehiculoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehículo", id));
    }
}
