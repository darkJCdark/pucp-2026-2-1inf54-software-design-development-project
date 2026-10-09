package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.almacenes.persistence.WarehouseEntity;
import com.pucp.paqrap.modulos.almacenes.repository.WarehouseRepository;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleParameters;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.MaintenanceDayEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.MaintenanceDayRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.incidencias.persistence.BreakdownEventEntity;
import com.pucp.paqrap.modulos.incidencias.repository.BreakdownEventRepository;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Datos operativos leídos de MySQL; reemplaza a {@link FuenteDatosOperativosMock}. Mantenimientos y averías se
 * devuelven completos: el snapshot de SA ya filtra por instante cuáles afectan a cada vehículo.
 */
@Primary
@Component
public class FuenteDatosOperativosBd implements FuenteDatosOperativos {

    private final VehicleRepository vehiculoRepository;
    private final VehicleTypeParametersRepository tipoRepository;
    private final WarehouseRepository almacenRepository;
    private final MaintenanceDayRepository mantenimientoRepository;
    private final BreakdownEventRepository averiaRepository;

    public FuenteDatosOperativosBd(VehicleRepository vehiculoRepository, VehicleTypeParametersRepository tipoRepository,
                                  WarehouseRepository almacenRepository, MaintenanceDayRepository mantenimientoRepository,
                                  BreakdownEventRepository averiaRepository) {
        this.vehiculoRepository = vehiculoRepository;
        this.tipoRepository = tipoRepository;
        this.almacenRepository = almacenRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.averiaRepository = averiaRepository;
    }

    @Override
    public List<Vehicle> flota() {
        return vehiculoRepository.findAll().stream().map(VehicleEntity::toDomain).toList();
    }

    @Override
    public Collection<Warehouse> almacenes() {
        return almacenRepository.findAll().stream().map(WarehouseEntity::toDomain).toList();
    }

    @Override
    public MaintenanceCalendar calendarioMantenimiento() {
        return new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE,
                mantenimientoRepository.findAll().stream().map(MaintenanceDayEntity::toDomain).toList());
    }

    @Override
    public List<BreakdownEvent> averias() {
        return averiaRepository.findAll().stream().map(BreakdownEventEntity::toDomain).toList();
    }

    /** Parámetros editables con PUT /api/flota/tipos/{tipo}. */
    @Override
    public FleetProfile perfilFlota() {
        Map<VehicleType, VehicleParameters> parametros = new EnumMap<>(VehicleType.class);
        for (VehicleTypeParametersEntity tipo : tipoRepository.findAll()) {
            parametros.put(tipo.getVehicleType(), tipo.toDomain());
        }
        return new FleetProfile(parametros);
    }
}
