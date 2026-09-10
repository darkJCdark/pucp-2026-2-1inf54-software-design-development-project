package pe.edu.pucp.paqrap.planner.sa;

import pe.edu.pucp.paqrap.planner.route.RoutePlan;

import java.util.Optional;
import java.util.random.RandomGenerator;

public interface NeighborGenerator {
    Optional<RoutePlan> generate(RoutePlan current, RandomGenerator random);
}
