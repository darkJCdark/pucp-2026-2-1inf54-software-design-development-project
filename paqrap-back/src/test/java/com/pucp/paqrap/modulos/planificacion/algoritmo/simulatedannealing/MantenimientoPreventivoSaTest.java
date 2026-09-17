package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.almacenes.entity.InventorySnapshot;
import com.pucp.paqrap.modulos.almacenes.entity.Warehouse;
import com.pucp.paqrap.modulos.flota.entity.FleetProfile;
import com.pucp.paqrap.modulos.flota.entity.Vehicle;
import com.pucp.paqrap.modulos.flota.entity.VehicleOperationalState;
import com.pucp.paqrap.modulos.flota.entity.VehicleStatus;
import com.pucp.paqrap.modulos.flota.entity.VehicleType;
import com.pucp.paqrap.modulos.flota.service.CargadorMantenimiento;
import com.pucp.paqrap.modulos.flota.service.MaintenanceCalendar;
import com.pucp.paqrap.modulos.flota.service.MaintenanceDay;
import com.pucp.paqrap.modulos.pedidos.entity.Order;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.OperationalPlanEvaluator;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.ResultadoPlanificacion;
import com.pucp.paqrap.modulos.planificacion.algoritmo.common.RouteScheduler;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;
import com.pucp.paqrap.modulos.planificacion.entity.ShiftSchedule;
import com.pucp.paqrap.modulos.redvial.entity.Location;
import com.pucp.paqrap.modulos.redvial.service.RoadNetwork;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MantenimientoPreventivoSaTest {
    @Test
    void noAsignaRutasALosVehiculosConMantenimientoRealTaTmYTb() throws Exception {
        List<MaintenanceDay> calendarioReal = new CargadorMantenimiento().cargar(recurso("mant.preventivo.09.10.txt"));
        verificarMantenimiento(calendarioReal, "TA01", VehicleType.CAR, "TA02");
        verificarMantenimiento(calendarioReal, "TM07", VehicleType.MOTORCYCLE, "TM01");
        verificarMantenimiento(calendarioReal, "TB01", VehicleType.BICYCLE, "TB02");
    }

    private void verificarMantenimiento(List<MaintenanceDay> calendarioReal, String idEnMantenimiento,
                                        VehicleType tipo, String idAlternativo) {
        MaintenanceDay registro = calendarioReal.stream().filter(dia -> dia.vehicleId().equals(idEnMantenimiento)).findFirst()
                .orElseThrow(() -> new AssertionError("El archivo real no contiene " + idEnMantenimiento));
        Instant instante = registro.date().atTime(12, 0).atZone(ShiftSchedule.DEFAULT_ZONE).toInstant();
        Warehouse central = Warehouse.central("CENTRAL", new Location(10, 10));
        Vehicle detenido = new Vehicle(idEnMantenimiento, tipo, true);
        Vehicle alternativo = new Vehicle(idAlternativo, tipo, true);
        Map<String, VehicleOperationalState> estados = List.of(detenido, alternativo).stream().collect(Collectors.toMap(Vehicle::id,
                vehiculo -> new VehicleOperationalState(vehiculo, VehicleStatus.AVAILABLE, central.location(), instante)));
        OperationalSnapshot snapshot = new OperationalSnapshot(instante, FleetProfile.defaults(), InventorySnapshot.from(List.of(central)),
                estados, new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, calendarioReal), ShiftSchedule.defaultSchedule(), List.of());
        Order estimulo = new Order("ESTIMULO-" + idEnMantenimiento, new Location(10, 12), 2, instante, instante.plusSeconds(4 * 3_600L));

        ResultadoPlanificacion resultado = new OperationalSimulatedAnnealingPlanner(evaluador()).planificar(snapshot, List.of(estimulo),
                List.of(), new AnnealingConfig(100.0, 1.0, 0.90, 2, 10, 10), random(idEnMantenimiento.hashCode()));

        assertFalse(snapshot.isVehiclePlannableAt(idEnMantenimiento, instante));
        assertTrue(snapshot.isVehiclePlannableAt(idAlternativo, instante));
        assertTrue(resultado.esFactible(), () -> "violaciones: " + resultado.evaluacion().violations());
        assertTrue(resultado.plan().routeForVehicle(idEnMantenimiento).isEmpty());
        assertTrue(resultado.plan().routeForVehicle(idAlternativo).isPresent());
        System.out.printf("SA mantenimiento real: %s (%s) sin ruta; %s atendio el estimulo.%n",
                idEnMantenimiento, registro.date(), idAlternativo);
    }

    private OperationalPlanEvaluator evaluador() {
        return new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
    }

    private RandomGenerator random(long semilla) {
        return RandomGeneratorFactory.<RandomGenerator>of("L64X128MixRandom").create(semilla);
    }

    private Path recurso(String nombre) throws URISyntaxException {
        return Path.of(getClass().getResource("/datos-profesor/" + nombre).toURI());
    }
}
