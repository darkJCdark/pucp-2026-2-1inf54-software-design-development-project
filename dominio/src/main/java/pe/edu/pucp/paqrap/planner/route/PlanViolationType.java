package pe.edu.pucp.paqrap.planner.route;

public enum PlanViolationType {
    UNKNOWN_VEHICLE,
    VEHICLE_UNAVAILABLE,
    INVALID_ROUTE_START,
    INVALID_INITIAL_LOAD,
    ROUTE_NOT_RETURNED_TO_WAREHOUSE,
    NO_ROAD_PATH,
    VEHICLE_CAPACITY,
    NEGATIVE_LOAD,
    UNKNOWN_ORDER,
    PARTIAL_DELIVERY_MISMATCH,
    SLA_MISSED,
    INSUFFICIENT_INVENTORY,
    RETURN_TO_EMPTY_WAREHOUSE,
    MAINTENANCE_OR_BREAKDOWN,
    /** A single leg between two consecutive stops exceeds the fleet sheet's
     *  "Maxima distancia ida: 80 km" limit. Interpretation adopted: applies
     *  to each individual leg, not to cumulative distance since the last
     *  warehouse departure -- see RouteScheduler / this type's usage. */
    LEG_DISTANCE_EXCEEDED
}
