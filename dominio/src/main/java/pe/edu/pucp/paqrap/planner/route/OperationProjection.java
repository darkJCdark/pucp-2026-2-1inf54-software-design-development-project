package pe.edu.pucp.paqrap.planner.route;

import pe.edu.pucp.paqrap.planner.domain.InventorySnapshot;
import pe.edu.pucp.paqrap.planner.domain.VehicleOperationalState;

import java.util.Map;
import java.util.Objects;

/** Operational state reconstructed at a specific simulated instant. */
public record OperationProjection(InventorySnapshot inventory,
                                  Map<String, VehicleOperationalState> vehicleStates,
                                  Map<String, Integer> deliveredPackagesByOrderId) {
    public OperationProjection {
        Objects.requireNonNull(inventory, "inventory is required");
        vehicleStates = Map.copyOf(Objects.requireNonNull(vehicleStates, "vehicleStates are required"));
        deliveredPackagesByOrderId = Map.copyOf(Objects.requireNonNull(deliveredPackagesByOrderId,
                "deliveredPackagesByOrderId is required"));
    }
}
