package pe.pucp.paqrap.modelo;

import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.route.*;
import java.util.*;

/** Evaluation always includes original demand; route validity and full coverage are separate. */
public record ResultadoPlanificacion(OperationalPlan plan, PlanEvaluation evaluacion, List<Order> noAtendidos) {
    public ResultadoPlanificacion { Objects.requireNonNull(plan); Objects.requireNonNull(evaluacion); noAtendidos=List.copyOf(noAtendidos); }
    public double costoTotal() { return evaluacion.totalCost(); }
    public boolean esFactible() { return evaluacion.isRouteFeasible(); }
    public boolean esCompleta() { return evaluacion.isFeasible() && noAtendidos.isEmpty(); }
    public boolean requiereReplanificacion() { return !esCompleta(); }
    // No esColapso(): a heuristic failure is not a certificate of logistical impossibility.
}
