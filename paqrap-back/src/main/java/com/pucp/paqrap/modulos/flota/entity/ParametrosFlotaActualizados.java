package com.pucp.paqrap.modulos.flota.entity;

import java.util.Objects;

/**
 * Evento de aplicación: cambiaron la velocidad, la capacidad o el costo por km de un tipo de vehículo. El motor de
 * escenarios lo usa para replanificar, porque el planificador lee estos parámetros de la BD en cada ciclo.
 */
public record ParametrosFlotaActualizados(VehicleType tipo) {
    public ParametrosFlotaActualizados {
        Objects.requireNonNull(tipo, "tipo es requerido");
    }
}
