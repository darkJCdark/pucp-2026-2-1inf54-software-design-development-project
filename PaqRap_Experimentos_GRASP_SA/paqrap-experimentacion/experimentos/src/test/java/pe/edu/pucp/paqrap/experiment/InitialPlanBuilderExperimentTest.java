package pe.edu.pucp.paqrap.experiment;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.*;
import pe.edu.pucp.paqrap.planner.route.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InitialPlanBuilderExperimentTest {
    private static final Set<String> S07_UNATTENDED = Set.of(
            "c3274-2026-09-3799",
            "c4638-2026-09-3800",
            "c9729-2026-09-3806");
    private static final Set<String> S08_UNATTENDED = Set.of(
            "c8715-2026-09-3985",
            "c0901-2026-09-4013",
            "c1413-2026-09-4015",
            "c3076-2026-09-3994");

    @Test
    void urgentOrderFallsBackFromPreferredBicycleToFirstFeasibleAlternative() {
        Instant start = Instant.parse("2026-09-09T12:00:00Z");
        Warehouse central = Warehouse.central("CENTRAL", PaqRapNetwork.CENTRAL_LOCATION);
        List<Vehicle> vehicles = List.of(vehicle(VehicleType.CAR, 1), vehicle(VehicleType.BICYCLE, 1),
                vehicle(VehicleType.MOTORCYCLE, 1));
        Map<String, VehicleOperationalState> states = new TreeMap<>();
        vehicles.forEach(vehicle -> states.put(vehicle.id(), new VehicleOperationalState(vehicle,
                VehicleStatus.AVAILABLE, central.location(), start)));
        OperationalSnapshot snapshot = new OperationalSnapshot(start, FleetProfile.defaults(),
                InventorySnapshot.from(List.of(central)), states,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                ShiftSchedule.defaultSchedule(), List.of());
        Order first = new Order("A-EASY", central.location(), 1, start, start.plus(Duration.ofHours(4)));
        Order urgent = new Order("B-URGENT", new Location(61, 30), 1, start,
                start.plus(Duration.ofHours(4)));

        SeedPlan seed = builder().build(snapshot, List.of(first, urgent), central, List.of());

        assertEquals(List.of(first, urgent), seed.attended());
        assertTrue(seed.unattended().isEmpty());
        assertTrue(seed.plan().routeForVehicle("TB01").isEmpty());
        assertTrue(delivers(seed.plan(), "TM01", urgent.id()));
        assertTrue(evaluator().evaluate(seed.plan(), snapshot, seed.attended(), List.of()).isFeasible());
    }

    @Test void s03BuildsCompleteFeasibleSeed() { assertScenario("S03", 96, Set.of()); }

    @Test void s04BuildsCompleteFeasibleSeed() { assertScenario("S04", 144, Set.of()); }

    @Test void s07KeepsValidPartialSeedAndExactUnattendedOrders() {
        assertScenario("S07", 38, S07_UNATTENDED);
    }

    @Test void s08KeepsValidPartialSeedAndRespectsRoadBlocks() {
        ScenarioResult result = assertScenario("S08", 49, S08_UNATTENDED);
        assertRoadBlocksRespected(result.evaluation(), result.instance().blocks());
    }

    @Test
    void splitOrderIsAdmittedCompletelyOrNotAtAll() {
        Instant start = Instant.parse("2026-09-09T12:00:00Z");
        Warehouse central = Warehouse.central("CENTRAL", PaqRapNetwork.CENTRAL_LOCATION);
        List<Vehicle> vehicles = List.of(vehicle(VehicleType.CAR, 1), vehicle(VehicleType.MOTORCYCLE, 1));
        Map<String, VehicleOperationalState> states = new TreeMap<>();
        vehicles.forEach(vehicle -> states.put(vehicle.id(), new VehicleOperationalState(vehicle,
                VehicleStatus.AVAILABLE, central.location(), start)));
        OperationalSnapshot snapshot = new OperationalSnapshot(start, FleetProfile.defaults(),
                InventorySnapshot.from(List.of(central)), states,
                new MaintenanceCalendar(ShiftSchedule.DEFAULT_ZONE, List.of()),
                ShiftSchedule.defaultSchedule(), List.of());
        Order impossible = new Order("A-IMPOSSIBLE-SPLIT", new Location(66, 49), 30, start,
                start.plus(Duration.ofMinutes(30)));
        Order feasible = new Order("B-FEASIBLE-SPLIT", new Location(28, 14), 30, start,
                start.plus(Duration.ofHours(12)));

        SeedPlan seed = builder().build(snapshot, List.of(impossible, feasible), central, List.of());
        List<DeliveryStop> stops = seed.plan().routes().stream().flatMap(route -> route.stops().stream())
                .filter(DeliveryStop.class::isInstance).map(DeliveryStop.class::cast).toList();

        assertEquals(List.of(feasible), seed.attended());
        assertEquals(List.of(impossible), seed.unattended());
        assertEquals(0, stops.stream().filter(stop -> stop.order().equals(impossible)).count());
        assertTrue(stops.stream().filter(stop -> stop.order().equals(feasible)).count() >= 2);
        assertEquals(30, stops.stream().filter(stop -> stop.order().equals(feasible))
                .mapToInt(DeliveryStop::deliveredPackages).sum());
        assertTrue(evaluator().evaluate(seed.plan(), snapshot, seed.attended(), List.of()).isFeasible());
    }

    private static ScenarioResult assertScenario(String id, int expectedAttended, Set<String> expectedUnattended) {
        ProblemInstance instance = scenario(id);
        SeedPlan seed = builder().build(instance.snapshot(), instance.orders(), instance.central(), instance.blocks());
        PlanEvaluation evaluation = evaluator().evaluate(seed.plan(), instance.snapshot(), seed.attended(),
                instance.blocks());
        CommonAudit.AuditResult audit = CommonAudit.evaluate(instance, seed.plan());
        assertEquals(expectedAttended, seed.attended().size());
        assertEquals(expectedUnattended, seed.unattended().stream().map(Order::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(instance.orders().size(), seed.attended().size() + seed.unattended().size());
        assertTrue(evaluation.isFeasible(), () -> id + ": " + evaluation.violations());
        assertTrue(audit.routeValid(), () -> id + ": " + audit.details());
        assertEquals(expectedUnattended.size(), audit.missingOrders());
        assertEquals(expectedUnattended.isEmpty(), audit.fullFeasible());
        return new ScenarioResult(instance, seed, evaluation);
    }

    private static ProblemInstance scenario(String id) {
        Path root = Files.isDirectory(Path.of("data")) ? Path.of(".") : Path.of("..");
        root = root.toAbsolutePath().normalize();
        ExperimentConfig config;
        try {
            config = ExperimentConfig.read(root.resolve("config/escalabilidad.properties"), root);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        ScenarioSpec spec = switch (id) {
            case "S03" -> new ScenarioSpec(id, "NORMAL", "SYNTHETIC", 96, 5102,
                    LocalDate.of(2026, 9, 9), 7, 8);
            case "S04" -> new ScenarioSpec(id, "NORMAL", "SYNTHETIC", 144, 5103,
                    LocalDate.of(2026, 9, 9), 7, 8);
            case "S07" -> new ScenarioSpec(id, "REAL", "REAL", 0, 0,
                    LocalDate.of(2026, 9, 23), 7, 11);
            case "S08" -> new ScenarioSpec(id, "REAL", "REAL", 0, 0,
                    LocalDate.of(2026, 9, 24), 7, 15);
            default -> throw new IllegalArgumentException("Unknown scenario: " + id);
        };
        return InstanceFactory.create(spec, config);
    }

    private static void assertRoadBlocksRespected(PlanEvaluation evaluation, List<RoadBlock> blocks) {
        assertTrue(evaluation.schedulesByRouteId().values().stream()
                .flatMap(route -> route.scheduledStops().stream())
                .flatMap(stop -> stop.approach().legs().stream())
                .noneMatch(leg -> blocks.stream().anyMatch(block -> block.overlaps(leg.departsAt(), leg.arrivesAt())
                        && (block.blockedSegments().contains(new StreetSegment(leg.from(), leg.to()))
                        || block.blockedNodes().contains(leg.from())
                        || block.blockedNodes().contains(leg.to())))));
    }

    private static boolean delivers(OperationalPlan plan, String vehicleId, String orderId) {
        return plan.routeForVehicle(vehicleId).stream().flatMap(route -> route.stops().stream())
                .filter(DeliveryStop.class::isInstance).map(DeliveryStop.class::cast)
                .anyMatch(stop -> stop.order().id().equals(orderId));
    }

    private static Vehicle vehicle(VehicleType type, int number) {
        return new Vehicle(type.fleetCode() + String.format("%02d", number), type, true);
    }

    private static InitialPlanBuilder builder() { return new InitialPlanBuilder(evaluator()); }

    private static OperationalPlanEvaluator evaluator() {
        return new OperationalPlanEvaluator(new RouteScheduler(new RoadNetwork()));
    }

    private record ScenarioResult(ProblemInstance instance, SeedPlan seed, PlanEvaluation evaluation) {}
}
