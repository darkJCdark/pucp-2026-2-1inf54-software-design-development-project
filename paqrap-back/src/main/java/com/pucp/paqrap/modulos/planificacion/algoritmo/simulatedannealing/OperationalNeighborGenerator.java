package com.pucp.paqrap.modulos.planificacion.algoritmo.simulatedannealing;

import com.pucp.paqrap.modulos.planificacion.entity.OperationalPlan;
import com.pucp.paqrap.modulos.planificacion.entity.OperationalSnapshot;

import java.util.Optional;
import java.util.random.RandomGenerator;

/** Proposes one neighbor; feasibility belongs exclusively to the common evaluator. */
interface OperationalNeighborGenerator {
    Optional<OperationalPlan> generate(OperationalPlan current, OperationalSnapshot snapshot, RandomGenerator random);
}
