package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.Location;

/** A physical stop in an operational delivery route. */
public sealed interface RouteStop permits DeliveryStop, WarehouseVisit {
    Location location();
}
