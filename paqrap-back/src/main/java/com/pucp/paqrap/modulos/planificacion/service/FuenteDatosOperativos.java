package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;

import java.util.Collection;
import java.util.List;

/**
 * Origen de los datos operativos que el planificador necesita además de los pedidos y bloqueos.
 * Hoy lo implementa un mock; cuando el script MySQL esté en develop se agrega una implementación
 * con repositorios JPA sin cambiar {@link PlanificadorSaService}.
 */
public interface FuenteDatosOperativos {

    List<Vehicle> flota();

    Collection<Warehouse> almacenes();

    MaintenanceCalendar calendarioMantenimiento();

    List<BreakdownEvent> averias();

    default FleetProfile perfilFlota() {
        return FleetProfile.defaults();
    }
}
