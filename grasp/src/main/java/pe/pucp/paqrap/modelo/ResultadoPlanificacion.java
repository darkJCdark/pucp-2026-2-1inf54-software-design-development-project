package pe.pucp.paqrap.modelo;

import pe.edu.pucp.paqrap.planner.domain.Order;
import pe.edu.pucp.paqrap.planner.route.OperationalPlan;
import pe.edu.pucp.paqrap.planner.route.PlanEvaluation;

import java.util.List;

/**
 * Envuelve un OperationalPlan + PlanEvaluation del dominio compartido.
 * noAtendidos es un concepto propio de GRASP: pedidos que ninguna unidad
 * pudo atender en esta iteración, nunca enviados al evaluador para que no
 * vuelvan infactible el plan completo (decisión confirmada por el docente).
 */
public record ResultadoPlanificacion(OperationalPlan plan, PlanEvaluation evaluacion, List<Order> noAtendidos) {
    public double costoTotal() {
        return evaluacion.totalCost();
    }

    public boolean esFactible() {
        return evaluacion.isFeasible();
    }
}
