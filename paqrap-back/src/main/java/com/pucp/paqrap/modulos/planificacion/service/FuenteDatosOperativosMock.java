package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.service.InicializadorFlota;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Datos de prueba mientras no exista la base de datos: 37 vehículos de InicializadorFlota,
 * el almacén central y los dos intermedios, sin mantenimientos ni averías.
 */
@Component
public class FuenteDatosOperativosMock implements FuenteDatosOperativos {

    @Override
    public List<Vehicle> flota() {
        return InicializadorFlota.crearFlotaInicial();
    }

    @Override
    public Collection<Warehouse> almacenes() {
        return List.of(
                Warehouse.central("CENTRAL", new Location(27, 14)),
                Warehouse.intermediate("NOROESTE", new Location(12, 38), 1_000),
                Warehouse.intermediate("ESTE", new Location(57, 27), 1_000));
    }

    @Override
    public MaintenanceCalendar calendarioMantenimiento() {
        return new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of());
    }

    @Override
    public List<BreakdownEvent> averias() {
        return List.of();
    }
}
