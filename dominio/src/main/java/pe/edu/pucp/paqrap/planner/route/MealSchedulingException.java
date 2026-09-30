package pe.edu.pucp.paqrap.planner.route;

/** Infeasible rest timing is a hard route violation, never an optional interruption. */
public final class MealSchedulingException extends IllegalStateException {
    public MealSchedulingException(String message) { super(message); }
}
