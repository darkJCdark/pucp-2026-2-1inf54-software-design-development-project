package com.pucp.paqrap.modulos.planificacion.service;

import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.incidencias.entity.BreakdownEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Origen de los datos operativos que el planificador necesita además de los pedidos y bloqueos.
 * Hoy lo implementa un mock; cuando el script MySQL esté en develop se agrega una implementación
 * con repositorios JPA sin cambiar {@link PlanificadorSaService}.
 */
public interface FuenteDatosOperativos {

    List<Vehicle> flota();

    Collection<Warehouse> almacenes();

    MaintenanceCalendar calendarioMantenimiento();

    /** Averías de la ejecución indicada; con {@code null}, las registradas fuera de los escenarios. */
    List<BreakdownEvent> averias(Long ejecucionId);

    default Map<String, VehicleOperationalState> estadosFlota(
            Instant instante
    ) {

        Warehouse central =
                almacenes()
                        .stream()
                        .filter(
                                Warehouse::isCentral
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "La fuente de datos no define un almacén central"
                                        )
                        );

        Map<String, VehicleOperationalState> estados =
                new LinkedHashMap<>();

        for (
                Vehicle vehiculo :
                flota()
        ) {

            estados.put(
                    vehiculo.id(),
                    new VehicleOperationalState(
                            vehiculo,
                            VehicleStatus.AVAILABLE,
                            central.location(),
                            instante
                    )
            );
        }

        return Map.copyOf(
                estados
        );
    }

    default FleetProfile perfilFlota() {
        return FleetProfile.defaults();
    }
}
