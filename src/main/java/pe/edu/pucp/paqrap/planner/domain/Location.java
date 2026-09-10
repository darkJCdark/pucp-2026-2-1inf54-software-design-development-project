package pe.edu.pucp.paqrap.planner.domain;

import java.util.Objects;

/** A node in PaqRap's orthogonal street grid. */
public record Location(int x, int y) {
    public double manhattanDistanceTo(Location other) {
        Objects.requireNonNull(other, "other location is required");
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }
}
