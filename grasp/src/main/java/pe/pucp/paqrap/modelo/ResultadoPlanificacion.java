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

    /** Senal explicita de colapso logistico: quedo al menos un pedido sin
     *  atender en el MEJOR resultado que GraspPlanificador.planificar()
     *  encontro tras todas sus iteraciones (construccion + busqueda local +
     *  reparacion de pendientes). No es lo mismo que esFactible()==false --
     *  un plan puede ser factible (sus rutas cumplen todas las reglas) y
     *  aun asi haber colapsado porque no le alcanzo la flota/tiempo para
     *  cubrir toda la demanda. Se calcula sobre el resultado FINAL ya
     *  elegido, no por iteracion intermedia -- evita tener que restar
     *  "49 de 50" a mano para notar que el escenario no se resolvio del
     *  todo.
     */
    public boolean esColapso() {
        return !noAtendidos.isEmpty();
    }
}
