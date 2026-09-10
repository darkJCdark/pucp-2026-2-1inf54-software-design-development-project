package pe.edu.pucp.paqrap.planner.sa;

import org.junit.jupiter.api.Test;
import pe.edu.pucp.paqrap.planner.domain.Location;
import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.domain.Vehicle;
import pe.edu.pucp.paqrap.planner.domain.VehicleType;
import pe.edu.pucp.paqrap.planner.domain.Warehouse;
import pe.edu.pucp.paqrap.planner.route.PlanEvaluator;
import pe.edu.pucp.paqrap.planner.route.RouteAssignmentService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Framework-free executable smoke test for the first implementation block. */
class SimulatedAnnealingSmokeTest {
    @Test
    void returnsAFeasibleLegacyAnnealingPlan() {
        Instant start = Instant.parse("2026-09-09T07:00:00Z");
        var orders = List.of(
                new Order("P-1", new Location(30, 15), 4, start, start.plus(4, ChronoUnit.HOURS)),
                new Order("P-2", new Location(27, 18), 4, start, start.plus(4, ChronoUnit.HOURS)));
        var vehicles = List.of(new Vehicle("TA01", VehicleType.CAR, true));
        var central = Warehouse.central("CENTRAL", new Location(27, 14));
        var evaluator = new PlanEvaluator();
        var seed = new RouteAssignmentService(evaluator)
                .createInitialPlan(orders, vehicles, central, start)
                .orElseThrow(() -> new AssertionError("Expected a feasible seed"));

        var planner = new SimulatedAnnealingPlanner(evaluator, new RouteNeighborGenerator(),
                RandomGenerator.getDefault());
        var result = planner.optimize(seed, orders,
                new AnnealingConfig(100.0, 1.0, 0.90, 10, 200, 50));

        assertTrue(evaluator.isFeasible(result, orders));
        //para comprobar que el codigo funciona correctamente
        System.out.println("Simulación ejecutada con éxito.");
        // Reemplaza "optimizedPlan" por el nombre de la variable que guarda tu ruta final
         System.out.println("Costo Optimizado: " + evaluator.cost(result));
    }
}
