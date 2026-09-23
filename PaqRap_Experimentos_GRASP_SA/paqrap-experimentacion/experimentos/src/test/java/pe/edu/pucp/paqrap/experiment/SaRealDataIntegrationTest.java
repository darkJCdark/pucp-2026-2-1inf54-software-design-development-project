package pe.edu.pucp.paqrap.experiment;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;
import pe.pucp.paqrap.modelo.CargadorBloqueos;
import pe.pucp.paqrap.modelo.CargadorMantenimiento;
import pe.pucp.paqrap.modelo.CargadorPedidos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class SaRealDataIntegrationTest {
    @Test
    void plansSeptemberFirstAtTwoWithRealOrdersBlocksAndMaintenance() {
        Path root = Files.isDirectory(Path.of("data")) ? Path.of(".") : Path.of("..");
        root = root.toAbsolutePath().normalize();
        YearMonth month = YearMonth.of(2026, 9);
        Instant planning = LocalDate.of(2026, 9, 1).atTime(2, 0)
                .atZone(ShiftSchedule.DEFAULT_ZONE).toInstant();
        Path sales = root.resolve("data/ventas/ventas.202609.txt");
        Path blockFile = root.resolve("data/bloqueos/bloqueo.2609.txt");
        Path maintenanceFile = root.resolve("data/mant.preventivo.09.10.txt");
        assertTrue(Files.isRegularFile(sales));
        assertTrue(Files.isRegularFile(blockFile));
        assertTrue(Files.isRegularFile(maintenanceFile));

        List<Order> realOrders = CargadorPedidos.desdeArchivo(sales, month, ShiftSchedule.DEFAULT_ZONE,
                LocalDate.of(2026, 9, 1).atStartOfDay(ShiftSchedule.DEFAULT_ZONE).toInstant(), planning);
        List<RoadBlock> activeBlocks = CargadorBloqueos.desdeArchivo(blockFile, month,
                ShiftSchedule.DEFAULT_ZONE).stream().filter(block -> block.isActiveAt(planning)).toList();
        List<MaintenanceDay> maintenance = CargadorMantenimiento.desdeArchivo(maintenanceFile);
        assertFalse(realOrders.isEmpty());
        assertFalse(activeBlocks.isEmpty());
        assertTrue(maintenance.contains(new MaintenanceDay("TA01", LocalDate.of(2026, 9, 1))));

        Properties parameters = new Properties();
        parameters.setProperty("sa.maximumIterations", "30");
        parameters.setProperty("sa.maximumWithoutImprovement", "30");
        ExperimentConfig config = new ExperimentConfig(parameters, root);
        ScenarioSpec scenario = new ScenarioSpec("SA_REAL_0200", "REAL", "REAL", 0, 0,
                LocalDate.of(2026, 9, 1), 0, 2);
        ProblemInstance instance = InstanceFactory.create(scenario, config);
        assertEquals(realOrders, instance.orders());
        assertEquals(planning, instance.snapshot().planningTime());
        assertTrue(instance.blocks().containsAll(activeBlocks));
        assertFalse(instance.snapshot().isVehiclePlannableAt("TA01", planning));

        AlgorithmOutput output = new SaAdapter().solve(instance, config, 202609010200L);
        CommonAudit.AuditResult audit = CommonAudit.evaluate(instance, output.plan());
        PlanEvaluation evaluation = new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()))
                .evaluate(output.plan(), instance.snapshot(), instance.orders(), instance.blocks());
        assertFalse(output.plan().routes().isEmpty(), () -> output.termination() + ": " + output.detail());
        assertTrue(audit.fullFeasible(), () -> evaluation.violations().toString());
        assertTrue(output.plan().routeForVehicle("TA01").isEmpty());
        assertTrue(evaluation.violations().isEmpty());
        for (ScheduledDeliveryRoute route : evaluation.schedulesByRouteId().values()) {
            int capacity = instance.snapshot().fleetProfile()
                    .parametersFor(route.route().vehicle().type()).capacity();
            for (ScheduledRouteStop stop : route.scheduledStops()) {
                assertTrue(stop.loadBefore() >= 0 && stop.loadAfter() >= 0);
                assertTrue(stop.loadBefore() <= capacity && stop.loadAfter() <= capacity);
            }
        }
        assertTrue(evaluation.schedulesByRouteId().values().stream()
                .flatMap(route -> route.scheduledStops().stream())
                .filter(stop -> stop.stop() instanceof DeliveryStop)
                .allMatch(stop -> !stop.arrivedAt().isAfter(((DeliveryStop) stop.stop()).order().deadline())));
        assertTrue(evaluation.schedulesByRouteId().values().stream().flatMap(route -> route.scheduledStops().stream())
                .flatMap(stop -> stop.approach().legs().stream())
                .noneMatch(leg -> instance.blocks().stream().anyMatch(block ->
                        block.overlaps(leg.departsAt(), leg.arrivesAt()) &&
                                (block.blockedSegments().contains(new StreetSegment(leg.from(), leg.to()))
                                        || block.blockedNodes().contains(leg.from())
                                        || block.blockedNodes().contains(leg.to())))));
    }

}
