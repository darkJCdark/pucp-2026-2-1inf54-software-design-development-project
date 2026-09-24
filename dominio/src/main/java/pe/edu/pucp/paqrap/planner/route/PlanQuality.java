package pe.edu.pucp.paqrap.planner.route;

/** No money penalties: feasibility, complete-order coverage, package coverage, cost, distance. */
public final class PlanQuality {
    private PlanQuality() {}
    /** Negative means a is better. */
    public static int compare(PlanEvaluation a, PlanEvaluation b) {
        if (b == null) return -1;
        int c = Boolean.compare(b.isRouteFeasible(), a.isRouteFeasible());
        if (c != 0) return c;
        c = Integer.compare(a.missingOrders(), b.missingOrders());
        if (c != 0) return c;
        c = Long.compare(a.missingUnits(), b.missingUnits());
        if (c != 0) return c;
        c = Double.compare(a.totalCost(), b.totalCost());
        return c != 0 ? c : Double.compare(a.totalDistanceKm(), b.totalDistanceKm());
    }
    public static boolean better(PlanEvaluation a, PlanEvaluation b) { return compare(a,b) < 0; }
    public static boolean sameCoverage(PlanEvaluation a, PlanEvaluation b) {
        return a.missingOrders() == b.missingOrders() && a.missingUnits() == b.missingUnits();
    }
}
