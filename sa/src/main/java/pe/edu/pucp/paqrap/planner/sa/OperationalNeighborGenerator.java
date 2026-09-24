package pe.edu.pucp.paqrap.planner.sa;

import pe.edu.pucp.paqrap.planner.domain.OperationalSnapshot;
import pe.edu.pucp.paqrap.planner.route.OperationalPlan;

import java.util.Optional;
import java.util.random.RandomGenerator;

/** Generates one neighboring assignment over the complete operational planning state. */
public interface OperationalNeighborGenerator {
    Optional<OperationalPlan> generate(OperationalPlan current, OperationalSnapshot snapshot, RandomGenerator random);
}
