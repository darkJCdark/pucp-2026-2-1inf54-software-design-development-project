package com.pucp.paqrap.modulos.flota.service;

import com.pucp.paqrap.comun.error.RecursoNoEncontradoException;
import com.pucp.paqrap.comun.error.ReglaNegocioException;
import com.pucp.paqrap.modulos.escenarios.entity.ScenarioStatus;
import com.pucp.paqrap.modulos.escenarios.repository.ScenarioExecutionRepository;
import com.pucp.paqrap.modulos.flota.dto.VehiculoResponse;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Cantidad de flota (C7): alta y baja de unidades por tipo sobre {@code vehicles}, sin cambios de esquema. El alta es
 * siempre posible; la baja se rechaza si la unidad va en ruta, si hay un escenario activo o si la unidad ya tiene
 * historial (la base de datos lo impide con sus llaves foráneas).
 */
@Service
@Transactional(readOnly = true)
public class FlotaCantidadService {

    private static final Set<ScenarioStatus> ESCENARIOS_ACTIVOS = Set.of(ScenarioStatus.RUNNING, ScenarioStatus.PAUSED);

    private final VehicleRepository vehiculoRepository;
    private final VehicleTypeParametersRepository tipoRepository;
    private final ScenarioExecutionRepository ejecucionRepository;

    public FlotaCantidadService(VehicleRepository vehiculoRepository, VehicleTypeParametersRepository tipoRepository,
                                ScenarioExecutionRepository ejecucionRepository) {
        this.vehiculoRepository = vehiculoRepository;
        this.tipoRepository = tipoRepository;
        this.ejecucionRepository = ejecucionRepository;
    }

    /** Crea la siguiente unidad del tipo (código de flota + número correlativo de dos dígitos o más). */
    @Transactional
    public VehiculoResponse alta(VehicleType tipo) {
        if (!tipoRepository.existsById(tipo)) {
            throw new RecursoNoEncontradoException("Tipo de vehículo", tipo);
        }
        String codigo = tipo.fleetCode();
        int ultimo = vehiculoRepository.findAll().stream()
                .map(VehicleEntity::getVehicleId)
                .filter(id -> id.startsWith(codigo))
                .mapToInt(id -> numero(id.substring(codigo.length())))
                .max()
                .orElse(0);
        String id = String.format("%s%02d", codigo, ultimo + 1);
        if (id.length() > 8) {
            throw new ReglaNegocioException("Se alcanzó el máximo de unidades del tipo " + tipo);
        }
        VehicleEntity nueva = vehiculoRepository.save(new VehicleEntity(id, tipo, VehicleOperationalStatus.AVAILABLE));
        return VehiculoResponse.de(nueva);
    }

    @Transactional
    public void baja(String id) {
        VehicleEntity vehiculo = vehiculoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehículo", id));
        if (vehiculo.getOperationalStatus() == VehicleOperationalStatus.IN_ROUTE) {
            throw new ReglaNegocioException("El vehículo " + id + " está en ruta; no puede darse de baja");
        }
        if (ejecucionRepository.existsByStatusIn(ESCENARIOS_ACTIVOS)) {
            throw new ReglaNegocioException("No se puede dar de baja un vehículo con un escenario activo; deténgalo antes");
        }
        try {
            vehiculoRepository.delete(vehiculo);
            vehiculoRepository.flush();
        } catch (DataIntegrityViolationException excepcion) {
            throw new ReglaNegocioException("El vehículo " + id + " tiene historial (rutas, averías, mantenimientos o "
                    + "pedidos) y no puede darse de baja; márquelo como UNAVAILABLE");
        }
    }

    private static int numero(String sufijo) {
        try {
            return Integer.parseInt(sufijo);
        } catch (NumberFormatException excepcion) {
            return 0;
        }
    }
}
