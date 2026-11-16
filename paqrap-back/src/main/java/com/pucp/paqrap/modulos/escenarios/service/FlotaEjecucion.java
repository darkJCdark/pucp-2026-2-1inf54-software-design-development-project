package com.pucp.paqrap.modulos.escenarios.service;

import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.persistence.VehicleEntity;
import com.pucp.paqrap.modulos.flota.persistence.VehicleOperationalStatus;
import com.pucp.paqrap.modulos.flota.persistence.VehicleTypeParametersEntity;
import com.pucp.paqrap.modulos.flota.repository.VehicleRepository;
import com.pucp.paqrap.modulos.flota.repository.VehicleTypeParametersRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flota con la que corrió cada ejecución, para el reporte CSV: unidades operativas por tipo (las UNAVAILABLE no
 * cuentan) y velocidades al iniciar y al final (cambian si se editan en caliente). Se captura al iniciar porque la
 * flota de la BD puede cambiar después. Vive en memoria: tras un reinicio del backend no está disponible.
 */
@Component
public class FlotaEjecucion {

    public record Resumen(Map<VehicleType, Integer> unidades, Map<VehicleType, BigDecimal> velocidadInicial,
                          Map<VehicleType, BigDecimal> velocidadFinal) {
        public Resumen {
            unidades = Map.copyOf(unidades);
            velocidadInicial = Map.copyOf(velocidadInicial);
            velocidadFinal = Map.copyOf(velocidadFinal);
        }
    }

    private final VehicleRepository vehiculos;
    private final VehicleTypeParametersRepository tipos;
    private final Map<Long, Resumen> resumenes = new ConcurrentHashMap<>();

    public FlotaEjecucion(VehicleRepository vehiculos, VehicleTypeParametersRepository tipos) {
        this.vehiculos = vehiculos;
        this.tipos = tipos;
    }

    public void capturar(long ejecucionId) {
        Map<VehicleType, Integer> unidades = new EnumMap<>(VehicleType.class);
        for (VehicleType tipo : VehicleType.values()) {
            unidades.put(tipo, 0);
        }
        for (VehicleEntity vehiculo : vehiculos.findAll()) {
            if (vehiculo.getOperationalStatus() != VehicleOperationalStatus.UNAVAILABLE) {
                unidades.merge(vehiculo.getVehicleType(), 1, Integer::sum);
            }
        }
        Map<VehicleType, BigDecimal> velocidades = velocidades();
        resumenes.put(ejecucionId, new Resumen(unidades, velocidades, velocidades));
    }

    /** Tras un cambio de parámetros en caliente, registra la velocidad vigente como la final. */
    public void actualizarVelocidades(Collection<Long> ejecucionesActivas) {
        Map<VehicleType, BigDecimal> velocidades = velocidades();
        for (Long id : ejecucionesActivas) {
            resumenes.computeIfPresent(id, (clave, actual) ->
                    new Resumen(actual.unidades(), actual.velocidadInicial(), velocidades));
        }
    }

    public Optional<Resumen> resumen(long ejecucionId) {
        return Optional.ofNullable(resumenes.get(ejecucionId));
    }

    private Map<VehicleType, BigDecimal> velocidades() {
        Map<VehicleType, BigDecimal> velocidades = new EnumMap<>(VehicleType.class);
        for (VehicleTypeParametersEntity tipo : tipos.findAll()) {
            velocidades.put(tipo.getVehicleType(), tipo.getSpeedKmh());
        }
        return velocidades;
    }
}
